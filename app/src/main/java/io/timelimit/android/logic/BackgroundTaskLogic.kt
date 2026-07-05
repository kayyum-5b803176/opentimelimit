/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.logic

import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.backup.DatabaseBackup
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.data.model.ManipulationFlag
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.extensions.nextBlockedMinuteOfWeek
import io.timelimit.android.integration.platform.*
import io.timelimit.android.integration.platform.android.AccessibilityService
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.blockingreason.AppBaseHandling
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache
import io.timelimit.android.logic.blockingreason.CategoryItselfHandling
import io.timelimit.android.sync.actions.UpdateDeviceStatusAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.lock.LockActivity
import io.timelimit.android.util.AndroidVersion
import io.timelimit.android.util.TimeTextUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

class BackgroundTaskLogic(val appLogic: AppLogic) {
    var pauseForegroundAppBackgroundLoop = false
    val lastLoopException = MutableLiveData<Exception?>().apply { value = null }

    companion object {
        private const val LOG_TAG = "BackgroundTaskLogic"

        private const val CHECK_PERMISSION_INTERVAL = 10 * 1000L        // all 10 seconds
        // while the screen is off nobody can interactively change permissions, so a
        // lazy safety cadence is enough there; a check additionally runs right when
        // the screen turns on (see the wake handling in the main loop)
        private const val CHECK_PERMISSION_INTERVAL_SCREEN_OFF = 5 * 60 * 1000L

        // Single base interval for the main loop. The old 100ms "fast" mode existed to
        // make blocking snappy, but it is obsolete: whenever the accessibility service
        // is available, a foreground app change *pushes* a wake signal to this loop
        // (see loopWakeSignal below), which reacts faster than 100ms polling ever did -
        // while doing 10x fewer loop rounds. Without the accessibility service this
        // matches the previous fallback behavior exactly.
        private const val BACKGROUND_SERVICE_INTERVAL = 1000L           // every second
        private const val MAX_USED_TIME_PER_ROUND = 2000                // 2 seconds

        // While the screen is off and no audio is playing there is nothing to block
        // and nothing to measure - the loop only keeps a lazy safety poll (in case a
        // wake event is ever missed, e.g. playback starting without a media session
        // event coming through) and is otherwise woken instantly by screen-on/
        // playback events.
        private const val SCREEN_OFF_IDLE_INTERVAL = 15 * 1000L

        // lower bound between two rounds so that event bursts can never turn the
        // loop into a busy loop
        private const val MIN_ROUND_GAP = 100L

        const val EXTEND_SESSION_TOLERANCE = 5 * 1000L                  // 5 seconds
    }

    // Conflated wake signal: platform events (foreground app changed, screen state
    // changed, media playback changed) poke this channel; the loop waits on it with
    // its regular interval as timeout. This is what allows long sleep intervals
    // without giving up any reactivity. Conflation means an event burst causes at
    // most one extra round.
    private val loopWakeSignal = Channel<Unit>(capacity = Channel.CONFLATED)

    init {
        appLogic.platformIntegration.registerBackgroundLoopWakeListener {
            loopWakeSignal.trySend(Unit)
        }

        runAsyncExpectForever { backgroundServiceLoop() }
        runAsyncExpectForever { syncDeviceStatusLoop() }
        runAsyncExpectForever { backupDatabaseLoop() }
        runAsyncExpectForever { annoyUserOnManipulationLoop() }
        runAsync { checkForceKilled() }
        runAsync {
            // this is effective after an reboot

            if (appLogic.deviceEntryIfEnabled.waitForNullableValue() != null) {
                appLogic.platformIntegration.setEnableSystemLockdown(true)
            } else {
                appLogic.platformIntegration.setEnableSystemLockdown(false)
            }
        }

        appLogic.deviceEntryIfEnabled
                .map { it?.id }
                .ignoreUnchanged()
                .observeForever {
                    _ ->

                    runAsync {
                        syncInstalledAppVersion()
                    }
                }

        appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.CUSTOM_HOME_SCREEN).observeForever {
            appLogic.platformIntegration.setEnableCustomHomescreen(it)
        }

        appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.NETWORKTIME_AT_SYSTEMLEVEL).observeForever {
            appLogic.platformIntegration.setForceNetworkTime(it)
        }

        // note: the HIGH_MAIN_LOOP_DELAY experimental flag became a no-op - the main
        // loop always uses the slow interval now and gets its reactivity from push
        // events instead of fast polling
    }

    private val usedTimeUpdateHelper = UsedTimeUpdateHelper(appLogic)
    private var previousMainLogicExecutionTime = 0
    private var previousMainLoopEndTime = 0L
    private var previousAudioPlaybackBlock: Pair<Long, String>? = null
    private var previousMinuteOfWeek = -1
    private var previousIsScreenOn: Boolean? = null
    private val dayChangeTracker = DayChangeTracker(
            timeApi = appLogic.timeApi,
            longDuration = 1000 * 60 * 10 /* 10 minutes */
    )
    private val undisturbedCategoryUsageCounter = UndisturbedCategoryUsageCounter()

    /**
     * Waits until the next round should run: at most [timeout], but returns early
     * when a wake event (foreground app change, screen state change, media playback
     * change) arrives. A minimum gap is always enforced so event bursts can not
     * busy-loop. Events which arrive during a running round or during the minimum
     * gap are not lost - the channel is conflated, so they are consumed here and
     * trigger an immediate next round.
     */
    private suspend fun waitForNextRound(timeout: Long) {
        appLogic.timeApi.sleep(MIN_ROUND_GAP)

        val remaining = timeout - MIN_ROUND_GAP

        if (remaining > 0) {
            withTimeoutOrNull(remaining) { loopWakeSignal.receive() }
        }
    }

    /**
     * Handles the screen on/off transition bookkeeping of the main loop:
     * when the screen just turned on, the permission/manipulation status is checked
     * right away (the periodic check idles while the screen is off).
     */
    private fun reportScreenStateToLoop(isScreenOn: Boolean) {
        val previous = previousIsScreenOn
        previousIsScreenOn = isScreenOn

        if (previous == false && isScreenOn) {
            syncDeviceStatusAsync()
        }
    }

    private val appTitleCache = QueryAppTitleCache(appLogic.platformIntegration)
    private val categoryHandlingCache = CategoryHandlingCache()

    private val isChromeOs = appLogic.context.packageManager.hasSystemFeature(PackageManager.FEATURE_PC)

    private suspend fun openLockscreen(blockedAppPackageName: String, blockedAppActivityName: String?, enableSoftBlocking: Boolean) {
        if (enableSoftBlocking) {
            appLogic.platformIntegration.setShowBlockingOverlay(false)
        } else {
            appLogic.platformIntegration.setShowBlockingOverlay(true, "$blockedAppPackageName:${blockedAppActivityName?.removePrefix(blockedAppPackageName)}")
        }

        if (isChromeOs) {
            LockActivity.currentInstances.forEach { it.finish() }

            var i = 0

            while (LockActivity.currentInstances.isNotEmpty() && i < 2000) {
                delay(10)
                i += 10
            }
        }

        if (appLogic.platformIntegration.isAccessibilityServiceEnabled() && !enableSoftBlocking) {
            if (blockedAppPackageName != appLogic.platformIntegration.getLauncherAppPackageName()) {
                AccessibilityService.instance?.showHomescreen()
                delay(100)
            }
        }

        appLogic.platformIntegration.showAppLockScreen(blockedAppPackageName, blockedAppActivityName)
    }

    private var showNotificationToRevokeTemporarilyAllowedApps: Boolean? = null

    private fun setShowNotificationToRevokeTemporarilyAllowedApps(show: Boolean) {
        if (showNotificationToRevokeTemporarilyAllowedApps != show) {
            showNotificationToRevokeTemporarilyAllowedApps = show
            appLogic.platformIntegration.setShowNotificationToRevokeTemporarilyAllowedApps(show)
        }
    }

    private suspend fun commitUsedTimeUpdaters() {
        usedTimeUpdateHelper.flush()
    }

    private suspend fun backgroundServiceLoop() {
        while (true) {
            val loopStartTime = appLogic.timeApi.getCurrentUptimeInMillis()

            val backgroundServiceInterval = BACKGROUND_SERVICE_INTERVAL
            val maxUsedTimeToAdd = MAX_USED_TIME_PER_ROUND

            // app must be enabled
            if (!appLogic.enable.waitForNonNullValue()) {
                commitUsedTimeUpdaters()
                undisturbedCategoryUsageCounter.reset()
                appLogic.platformIntegration.setAppStatusMessage(null)
                appLogic.platformIntegration.setShowBlockingOverlay(false)
                setShowNotificationToRevokeTemporarilyAllowedApps(false)
                appLogic.enable.waitUntilValueMatches { it == true }

                continue
            }

            val deviceAndUSerRelatedData = Threads.database.executeAndWait {
                appLogic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()
            }

            val deviceRelatedData = deviceAndUSerRelatedData?.deviceRelatedData
            val userRelatedData = deviceAndUSerRelatedData?.userRelatedData

            setShowNotificationToRevokeTemporarilyAllowedApps(deviceRelatedData?.temporarilyAllowedApps?.isNotEmpty() ?: false)

            // device must be used by a child
            if (deviceRelatedData == null || userRelatedData == null || userRelatedData.user.type != UserType.Child) {
                commitUsedTimeUpdaters()
                undisturbedCategoryUsageCounter.reset()

                val isScreenOnNow = appLogic.platformIntegration.isScreenOn()
                reportScreenStateToLoop(isScreenOnNow)

                val shouldDoAutomaticSignOut = deviceRelatedData != null && DefaultUserLogic.hasAutomaticSignOut(deviceRelatedData) && deviceRelatedData.canSwitchToDefaultUser

                if (shouldDoAutomaticSignOut) {
                    appLogic.defaultUserLogic.reportScreenOn(isScreenOnNow)

                    appLogic.platformIntegration.setAppStatusMessage(
                            AppStatusMessage(
                                    title = appLogic.context.getString(R.string.background_logic_timeout_title),
                                    text = appLogic.context.getString(R.string.background_logic_timeout_text),
                                    showSwitchToDefaultUserOption = true
                            )
                    )
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                } else {
                    appLogic.platformIntegration.setAppStatusMessage(null)
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                }

                // nothing to block/measure here; while the screen is off there is
                // additionally no way the situation changes without an event which
                // pokes the wake signal (screen on, user change through the database
                // is caught at the latest by the safety interval)
                waitForNextRound(if (isScreenOnNow) backgroundServiceInterval else SCREEN_OFF_IDLE_INTERVAL)

                continue
            }

            // loop logic
            var idleBecauseScreenOffAndSilent = false

            try {
                // get the current time
                val nowTimestamp = appLogic.timeApi.getCurrentTimeInMillis()
                val nowTimezone = TimeZone.getTimeZone(userRelatedData.user.timeZone)
                val nowUptime = appLogic.timeApi.getCurrentUptimeInMillis()

                val nowDate = DateInTimezone.getLocalDate(nowTimestamp, nowTimezone)
                val nowMinuteOfWeek = getMinuteOfWeek(nowTimestamp, nowTimezone)
                val lastMinuteOfWeek = previousMinuteOfWeek.let { if (it < 0) nowMinuteOfWeek else it }
                previousMinuteOfWeek = nowMinuteOfWeek
                val dayOfEpoch = nowDate.toEpochDay().toInt()

                // eventually remove old used time data
                run {
                    val dayChange = dayChangeTracker.reportDayChange(dayOfEpoch)

                    fun deleteOldUsedTimes() = UsedTimeDeleter.deleteOldUsedTimeItems(
                            database = appLogic.database,
                            date = DateInTimezone.newInstance(nowDate),
                            timestamp = nowTimestamp
                    )

                    if (dayChange == DayChangeTracker.DayChange.NowSinceLongerTime) {
                        deleteOldUsedTimes()
                    }
                }

                // get the current status
                val isScreenOn = appLogic.platformIntegration.isScreenOn()
                val batteryStatus = appLogic.platformIntegration.getBatteryStatus()

                reportScreenStateToLoop(isScreenOn)
                appLogic.defaultUserLogic.reportScreenOn(isScreenOn)

                if (!isScreenOn) {
                    if (deviceRelatedData.temporarilyAllowedApps.isNotEmpty()) {
                        resetTemporarilyAllowedApps()
                    }
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "requesting foreground apps now (interval=$backgroundServiceInterval)")
                }

                val foregroundAppsOrNullOnMissingPermission = try {
                    appLogic.platformIntegration.getForegroundApps(
                        appLogic.getForegroundAppQueryInterval(),
                        deviceRelatedData.experimentalFlags
                    )
                } catch (ex: SecurityException) {
                    lastLoopException.postValue(ex)

                    null
                }
                val audioPlaybackPackageName = appLogic.platformIntegration.getMusicPlaybackPackage()
                val activityLevelBlocking = appLogic.deviceEntry.value?.enableActivityLevelBlocking ?: false

                // screen off + no audio playing = nothing that could be blocked and
                // nothing whose usage time could count -> the loop may idle after
                // this round until an event (screen on/audio playback) wakes it.
                // Exception: while the foreground app permission is missing, the
                // sanction logic counts usage time against all categories around the
                // clock - idling would weaken that sanction, so keep looping then.
                idleBecauseScreenOffAndSilent = (!isScreenOn) &&
                        audioPlaybackPackageName == null &&
                        foregroundAppsOrNullOnMissingPermission != null

                val foregroundAppWithBaseHandlings = if (foregroundAppsOrNullOnMissingPermission != null) {
                    foregroundAppsOrNullOnMissingPermission.map { app ->
                        app to AppBaseHandling.calculate(
                            foregroundAppPackageName = app.packageName,
                            foregroundAppActivityName = app.activityName,
                            pauseForegroundAppBackgroundLoop = pauseForegroundAppBackgroundLoop,
                            userRelatedData = userRelatedData,
                            deviceRelatedData = deviceRelatedData,
                            pauseCounting = !isScreenOn,
                            isSystemImageApp = appLogic.platformIntegration.isSystemImageApp(app.packageName)
                        )
                    }
                } else listOf(
                    ForegroundApp(DummyApps.MISSING_PERMISSION_APP, null) to AppBaseHandling.SanctionCountEverything(
                        categoryIds = userRelatedData.categoryById.keys
                    )
                )

                val backgroundAppBaseHandling = AppBaseHandling.calculate(
                        foregroundAppPackageName = audioPlaybackPackageName,
                        foregroundAppActivityName = DummyApps.ACTIVITY_BACKGROUND_AUDIO,
                        pauseForegroundAppBackgroundLoop = false,
                        userRelatedData = userRelatedData,
                        deviceRelatedData = deviceRelatedData,
                        pauseCounting = false,
                        isSystemImageApp = audioPlaybackPackageName?.let { appLogic.platformIntegration.isSystemImageApp(it) } ?: false
                )

                val allAppsBaseHandlings = foregroundAppWithBaseHandlings.map { it.second } + listOf(backgroundAppBaseHandling)

                undisturbedCategoryUsageCounter.report(
                    nowUptime,
                    AppBaseHandling.getCategories(
                        allAppsBaseHandlings,
                        AppBaseHandling.GetCategoriesPurpose.DelayedSessionDurationCounting
                    )
                )
                val recentlyStartedCategories = undisturbedCategoryUsageCounter.getRecentlyStartedCategories(nowUptime)

                val needsNetworkId = allAppsBaseHandlings.find { it.needsNetworkId } != null
                val networkId: NetworkId? = if (needsNetworkId) appLogic.platformIntegration.getCurrentNetworkId() else null

                fun reportStatusToCategoryHandlingCache(userRelatedData: UserRelatedData) {
                    categoryHandlingCache.reportStatus(
                            user = userRelatedData,
                            timeInMillis = nowTimestamp,
                            batteryStatus = batteryStatus,
                            currentNetworkId = networkId
                    )
                }; reportStatusToCategoryHandlingCache(userRelatedData)

                // check if should be blocked
                val blockedForegroundApp = foregroundAppWithBaseHandlings.find { (_, foregroundAppBaseHandling) ->
                    val noCategoryBlocking = foregroundAppBaseHandling is AppBaseHandling.BlockDueToNoCategory

                    val byCategoryBlocking = foregroundAppBaseHandling
                        .getCategories(AppBaseHandling.GetCategoriesPurpose.Blocking)
                        .find {
                            categoryHandlingCache.get(it).shouldBlockActivities
                        } != null

                    noCategoryBlocking || byCategoryBlocking
                }?.first

                val blockAudioPlayback = kotlin.run {
                    val noCategoryBlocking = backgroundAppBaseHandling is AppBaseHandling.BlockDueToNoCategory

                    val byCategoryBlocking = backgroundAppBaseHandling
                        .getCategories(AppBaseHandling.GetCategoriesPurpose.Blocking)
                        .find {
                            val handling = categoryHandlingCache.get(it)
                            val blockAllNotifications = handling.blockAllNotifications is CategoryItselfHandling.BlockAllNotifications.Yes

                            handling.shouldBlockActivities || blockAllNotifications
                        } != null

                    noCategoryBlocking || byCategoryBlocking
                }

                // update times
                val timeToSubtract = Math.min(previousMainLogicExecutionTime, maxUsedTimeToAdd)

                val categoryIdsToCount = AppBaseHandling.getCategories(
                    allAppsBaseHandlings,
                    AppBaseHandling.GetCategoriesPurpose.UsageCounting
                )

                val categoryHandlingsToCount = categoryIdsToCount
                        .map { categoryHandlingCache.get(it) }
                        .filter { it.shouldCountTime }

                fun timeToSubtractForCategory(categoryId: String): Int {
                    return if (usedTimeUpdateHelper.getCountedCategoryIds().contains(categoryId)) usedTimeUpdateHelper.getCountedTime() else 0
                }

                kotlin.run {
                    categoryHandlingsToCount.forEach { handling ->
                        val category = handling.createdWithCategoryRelatedData.category
                        val categoryId = category.id
                        val timeToSubtractForCategory = timeToSubtractForCategory(categoryId)
                        val nowRemaining = handling.remainingTime ?: return@forEach // category is not limited anymore

                        val oldRemainingTime = nowRemaining.includingExtraTime - timeToSubtractForCategory
                        val newRemainingTime = oldRemainingTime - timeToSubtract

                        val oldRemainingNonExtraTime = nowRemaining.default - timeToSubtractForCategory
                        val newRemainingNonExtraTime = oldRemainingNonExtraTime - timeToSubtract

                        val oldSessionDuration = handling.remainingSessionDuration?.let { it - timeToSubtractForCategory }

                        // trigger time warnings
                        fun handleTimeWarnings(
                            notificationTitleStringResource: Int,
                            roundedNewTimeInMilliseconds: Long
                        ) {
                            val roundedNewTimeInMinutes = roundedNewTimeInMilliseconds / (1000 * 60)

                            if (
                                // CategoryTimeWarning.MAX is still small enough for an integer
                                roundedNewTimeInMilliseconds >= 0 &&
                                roundedNewTimeInMilliseconds < Int.MAX_VALUE &&
                                roundedNewTimeInMinutes >= 0 &&
                                roundedNewTimeInMinutes < Int.MAX_VALUE &&
                                handling.createdWithCategoryRelatedData.allTimeWarningMinutes.contains(
                                    roundedNewTimeInMinutes.toInt()
                                )
                            ) {
                                appLogic.platformIntegration.showTimeWarningNotification(
                                    title = appLogic.context.getString(
                                        notificationTitleStringResource,
                                        category.title
                                    ),
                                    text = TimeTextUtil.remaining(
                                        roundedNewTimeInMilliseconds.toInt(),
                                        appLogic.context
                                    )
                                )
                            }
                        }

                        if (oldRemainingTime / (1000 * 60) != newRemainingTime / (1000 * 60)) {
                            handleTimeWarnings(
                                notificationTitleStringResource = R.string.time_warning_not_title,
                                roundedNewTimeInMilliseconds = ((newRemainingTime / (1000 * 60)) + 1) * 1000 * 60
                            )
                        }

                        if (oldRemainingNonExtraTime > 0 && newRemainingNonExtraTime <= 0) {
                            appLogic.platformIntegration.showExtraTimeStartedNotification(categoryId, category.title)
                        }

                        if (oldSessionDuration != null) {
                            val newSessionDuration = oldSessionDuration - timeToSubtract

                            if (oldSessionDuration / (1000 * 60) != newSessionDuration / (1000 * 60)) {
                                handleTimeWarnings(
                                    notificationTitleStringResource = R.string.time_warning_not_title_session,
                                    roundedNewTimeInMilliseconds = ((newSessionDuration / (1000 * 60)) + 1) * (1000 * 60)
                                )
                            }
                        }

                        if (handling.okByBlockedTimeAreas && nowMinuteOfWeek != lastMinuteOfWeek) {
                            val nextBlockedMinute = handling.createdWithCategoryRelatedData.nextBlockedMinuteOfWeek(nowMinuteOfWeek) ?: run {
                                handling.createdWithCategoryRelatedData.nextBlockedMinuteOfWeek(0)?.let { MinuteOfDay.LENGTH * 7 + it }
                            }

                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "next blocked minute: $nextBlockedMinute (current: $nowMinuteOfWeek)")
                            }

                            if (nextBlockedMinute != null) {
                                val minutesUntilNextBlockedMinute = nextBlockedMinute - nowMinuteOfWeek

                                handleTimeWarnings(
                                    notificationTitleStringResource = R.string.time_warning_not_title_blocked_time_area,
                                    roundedNewTimeInMilliseconds = minutesUntilNextBlockedMinute.toLong() * 1000 * 60
                                )
                            }
                        }
                    }
                }

                if (
                        usedTimeUpdateHelper.report(
                                duration = timeToSubtract,
                                dayOfEpoch = dayOfEpoch,
                                timestamp = nowTimestamp,
                                handlings = categoryHandlingsToCount,
                                recentlyStartedCategories = recentlyStartedCategories
                        )
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "auto commit used times")
                    }

                    val newDeviceAndUserRelatedData = Threads.database.executeAndWait {
                        appLogic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()
                    }

                    if (
                            newDeviceAndUserRelatedData?.userRelatedData?.user?.id != deviceAndUSerRelatedData.userRelatedData.user.id ||
                            newDeviceAndUserRelatedData.userRelatedData.categoryById.keys != deviceAndUSerRelatedData.userRelatedData.categoryById.keys
                    ) {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "restart the loop")
                        }

                        // start the loop directly again
                        continue
                    }

                    reportStatusToCategoryHandlingCache(userRelatedData = newDeviceAndUserRelatedData.userRelatedData)
                }

                // show notification
                fun buildStatusMessageWithCurrentAppTitle(
                        text: String,
                        titlePrefix: String = "",
                        titleSuffix: String = "",
                        appPackageName: String?,
                        appActivityToShow: String?
                ) = AppStatusMessage(
                        title = titlePrefix + appTitleCache.query(appPackageName ?: "invalid") + titleSuffix,
                        text = text,
                        subtext = if (appActivityToShow != null && appPackageName != null) appActivityToShow.removePrefix(appPackageName) else null,
                        showSwitchToDefaultUserOption = deviceRelatedData.canSwitchToDefaultUser
                )

                fun getCategoryTitle(categoryId: String?): String = categoryId.let { userRelatedData.categoryById[it]?.category?.title } ?: categoryId.toString()

                fun buildNotificationForAppWithCategoryUsage(
                        suffix: String,
                        appPackageName: String?,
                        appActivityToShow: String?,
                        categoryId: String
                ): AppStatusMessage {
                    val handling = categoryHandlingCache.get(categoryId)

                    val titlePrefix = getCategoryTitle(categoryId) + " - "

                    return if (handling.areLimitsTemporarilyDisabled) {
                        buildStatusMessageWithCurrentAppTitle(
                                text = appLogic.context.getString(R.string.background_logic_limits_disabled),
                                titlePrefix = titlePrefix,
                                titleSuffix = suffix,
                                appPackageName = appPackageName,
                                appActivityToShow = appActivityToShow
                        )
                    } else if (handling.remainingTime == null) {
                        buildStatusMessageWithCurrentAppTitle(
                                text = appLogic.context.getString(R.string.background_logic_no_timelimit),
                                titlePrefix = titlePrefix,
                                titleSuffix = suffix,
                                appPackageName = appPackageName,
                                appActivityToShow = appActivityToShow
                        )
                    } else {
                        val remainingTimeFromCache = handling.remainingTime
                        val timeSubtractedFromThisCategory = timeToSubtractForCategory(categoryId)
                        val realRemainingTimeDefault = (remainingTimeFromCache.default - timeSubtractedFromThisCategory).coerceAtLeast(0)
                        val realRemainingTimeWithExtraTime = (remainingTimeFromCache.includingExtraTime - timeSubtractedFromThisCategory).coerceAtLeast(0)
                        val realRemainingTimeUsingExtraTime = realRemainingTimeDefault == 0L && realRemainingTimeWithExtraTime > 0

                        val remainingSessionDuration = handling.remainingSessionDuration?.let { (it - timeToSubtractForCategory(categoryId)).coerceAtLeast(0) }

                        buildStatusMessageWithCurrentAppTitle(
                                text = if (realRemainingTimeUsingExtraTime)
                                    appLogic.context.getString(R.string.background_logic_using_extra_time, TimeTextUtil.remaining(realRemainingTimeWithExtraTime.toInt(), appLogic.context))
                                else if (remainingSessionDuration != null && remainingSessionDuration < realRemainingTimeDefault)
                                    TimeTextUtil.pauseIn(remainingSessionDuration.toInt(), appLogic.context)
                                else
                                    TimeTextUtil.remaining(realRemainingTimeDefault.toInt() ?: 0, appLogic.context),
                                titlePrefix = titlePrefix,
                                titleSuffix = suffix,
                                appPackageName = appPackageName,
                                appActivityToShow = appActivityToShow
                        )
                    }
                }

                fun buildNotificationForAppWithoutCategoryUsage(
                        handling: AppBaseHandling,
                        suffix: String,
                        appPackageName: String?,
                        appActivityToShow: String?
                ): AppStatusMessage = when (handling) {
                    is AppBaseHandling.UseCategories -> throw IllegalArgumentException()
                    AppBaseHandling.BlockDueToNoCategory -> throw IllegalArgumentException()
                    AppBaseHandling.PauseLogic -> AppStatusMessage(
                            title = appLogic.context.getString(R.string.background_logic_paused_title) + suffix,
                            text = appLogic.context.getString(R.string.background_logic_paused_text),
                            showSwitchToDefaultUserOption = deviceRelatedData.canSwitchToDefaultUser
                    )
                    is AppBaseHandling.Whitelist -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_whitelisted),
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    AppBaseHandling.TemporarilyAllowed -> buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_temporarily_allowed),
                            titleSuffix = suffix,
                            appPackageName = appPackageName,
                            appActivityToShow = appActivityToShow
                    )
                    AppBaseHandling.Idle -> AppStatusMessage(
                            appLogic.context.getString(R.string.background_logic_idle_title) + suffix,
                            appLogic.context.getString(R.string.background_logic_idle_text),
                            showSwitchToDefaultUserOption = deviceRelatedData.canSwitchToDefaultUser
                    )
                    is AppBaseHandling.SanctionCountEverything -> AppStatusMessage(
                        title = appLogic.context.getString(R.string.background_logic_permission_sanction_title) + suffix,
                        text = appLogic.context.getString(R.string.background_logic_permission_sanction_text),
                        showSwitchToDefaultUserOption = deviceRelatedData.canSwitchToDefaultUser
                    )
                }

                val showBackgroundStatus = !(backgroundAppBaseHandling is AppBaseHandling.Idle) &&
                        !blockAudioPlayback &&
                        foregroundAppWithBaseHandlings.find { (app, handling) ->
                            app.packageName == audioPlaybackPackageName &&
                                    handling.getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification) ==
                                    backgroundAppBaseHandling.getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification)
                        } == null

                val statusMessage = if (blockedForegroundApp != null) {
                    buildStatusMessageWithCurrentAppTitle(
                            text = appLogic.context.getString(R.string.background_logic_opening_lockscreen),
                            appPackageName = blockedForegroundApp.packageName,
                            appActivityToShow = if (activityLevelBlocking) blockedForegroundApp.activityName else null
                    )
                } else {
                    val pagesForTheForegroundApps = foregroundAppWithBaseHandlings.sumOf { (_, foregroundAppBaseHandling) ->
                        foregroundAppBaseHandling
                            .getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification)
                            .count()
                            .coerceAtLeast(1)
                    }

                    val pagesForTheBackgroundApp = if (showBackgroundStatus) {
                        backgroundAppBaseHandling.getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification)
                            .count()
                            .coerceAtLeast(1)
                    } else 0

                    val totalPages = pagesForTheForegroundApps.coerceAtLeast(1) + pagesForTheBackgroundApp
                    // while the screen is off nobody can see the notification, so don't
                    // rotate through the pages (each rotation would re-post the
                    // notification); rotation resumes automatically with the screen on
                    // because the page is derived from the current timestamp
                    val currentPage = if (isScreenOn) (nowTimestamp / 3000 % totalPages).toInt() else 0

                    val suffix = if (totalPages == 1) "" else " (${currentPage + 1} / $totalPages)"

                    if (currentPage < pagesForTheForegroundApps.coerceAtLeast(1)) {
                        if (pagesForTheForegroundApps == 0) {
                            buildNotificationForAppWithoutCategoryUsage(
                                    appPackageName = null,
                                    appActivityToShow = null,
                                    suffix = suffix,
                                    handling = AppBaseHandling.Idle
                            )
                        } else {
                            val pageWithin = currentPage

                            var listItemIndex = 0
                            var indexWithinListItem = 0
                            var totalIndex = 0

                            while (listItemIndex < foregroundAppWithBaseHandlings.size) {
                                val item = foregroundAppWithBaseHandlings[listItemIndex]
                                val handling = item.second
                                val itemLength = handling
                                    .getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification)
                                    .count()
                                    .coerceAtLeast(1)

                                if (pageWithin < totalIndex + itemLength) {
                                    indexWithinListItem = pageWithin - totalIndex
                                    break
                                }

                                totalIndex += itemLength
                                listItemIndex++
                            }

                            val (app, handling) = foregroundAppWithBaseHandlings[listItemIndex]

                            val categoryId = handling
                                .getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification)
                                .elementAtOrNull(indexWithinListItem)

                            if (categoryId != null) {
                                buildNotificationForAppWithCategoryUsage(
                                        appPackageName = app.packageName,
                                        appActivityToShow = if (activityLevelBlocking) app.activityName else null,
                                        suffix = suffix,
                                        categoryId = categoryId
                                )
                            } else {
                                buildNotificationForAppWithoutCategoryUsage(
                                        appPackageName = app.packageName,
                                        appActivityToShow = if (activityLevelBlocking) app.activityName else null,
                                        suffix = suffix,
                                        handling = handling
                                )
                            }
                        }
                    } else {
                        val pageWithin = currentPage - pagesForTheForegroundApps

                        val categoryId = backgroundAppBaseHandling
                            .getCategories(AppBaseHandling.GetCategoriesPurpose.ShowingInStatusNotification)
                            .asIterable()
                            .elementAtOrNull(pageWithin)

                        if (categoryId != null) {
                            buildNotificationForAppWithCategoryUsage(
                                    appPackageName = audioPlaybackPackageName,
                                    appActivityToShow = if (activityLevelBlocking) DummyApps.ACTIVITY_BACKGROUND_AUDIO else null,
                                    suffix = suffix,
                                    categoryId = categoryId
                            )
                        } else {
                            buildNotificationForAppWithoutCategoryUsage(
                                    appPackageName = audioPlaybackPackageName,
                                    appActivityToShow = if (activityLevelBlocking) DummyApps.ACTIVITY_BACKGROUND_AUDIO else null,
                                    suffix = suffix,
                                    handling = backgroundAppBaseHandling
                            )
                        }
                    }
                }

                appLogic.platformIntegration.setAppStatusMessage(statusMessage)

                // handle blocking
                if (blockedForegroundApp != null) {
                    openLockscreen(
                            blockedAppPackageName = blockedForegroundApp.packageName,
                            blockedAppActivityName = blockedForegroundApp.activityName,
                            enableSoftBlocking = deviceRelatedData.experimentalFlags and ExperimentalFlags.ENABLE_SOFT_BLOCKING == ExperimentalFlags.ENABLE_SOFT_BLOCKING
                    )
                } else {
                    appLogic.platformIntegration.setShowBlockingOverlay(false)
                }

                if (blockAudioPlayback && audioPlaybackPackageName != null) {
                    val currentAudioBlockUptime = appLogic.timeApi.getCurrentUptimeInMillis()
                    val oldAudioPlaybackBlock = previousAudioPlaybackBlock
                    val skipAudioBlock = oldAudioPlaybackBlock != null &&
                            oldAudioPlaybackBlock.second == audioPlaybackPackageName &&
                            oldAudioPlaybackBlock.first >= currentAudioBlockUptime

                    if (!skipAudioBlock) {
                        val newAudioPlaybackBlock = currentAudioBlockUptime + 1000 * 10 /* block for 10 seconds */ to audioPlaybackPackageName

                        previousAudioPlaybackBlock = newAudioPlaybackBlock

                        runAsync {
                            if (appLogic.platformIntegration.muteAudioIfPossible(audioPlaybackPackageName)) {
                                appLogic.platformIntegration.showOverlayMessage(
                                    appLogic.context.getString(
                                        R.string.background_logic_toast_block_audio,
                                        appTitleCache.query(audioPlaybackPackageName)
                                    )
                                )

                                // allow blocking again
                                // no locking needed because everything happens on the main thread
                                if (previousAudioPlaybackBlock === newAudioPlaybackBlock) {
                                    previousAudioPlaybackBlock = appLogic.timeApi.getCurrentUptimeInMillis() + 1000 * 1 /* block for 1 more second */ to audioPlaybackPackageName
                                }
                            } else {
                                appLogic.platformIntegration.showOverlayMessage(appLogic.context.getString(R.string.background_logic_toast_block_audio_failed))
                            }
                        }
                    }
                }
            } catch (ex: SecurityException) {
                // this is handled by an other main loop (with a delay)
                lastLoopException.postValue(ex)

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_permission),
                        showSwitchToDefaultUserOption = deviceRelatedData.canSwitchToDefaultUser
                ))
                appLogic.platformIntegration.setShowBlockingOverlay(false)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "exception during running main loop", ex)
                }

                lastLoopException.postValue(ex)

                appLogic.platformIntegration.setAppStatusMessage(AppStatusMessage(
                        appLogic.context.getString(R.string.background_logic_error),
                        appLogic.context.getString(R.string.background_logic_error_internal),
                        showSwitchToDefaultUserOption = deviceRelatedData.canSwitchToDefaultUser,
                        showErrorMessage = true
                ))
                appLogic.platformIntegration.setShowBlockingOverlay(false)
            }

            // delay before running next time
            val endTime = appLogic.timeApi.getCurrentUptimeInMillis()
            previousMainLogicExecutionTime = (endTime - previousMainLoopEndTime).toInt()
            previousMainLoopEndTime = endTime

            // Use only this round's own execution time (loopStartTime -> endTime), not
            // previousMainLogicExecutionTime (which spans the previous round's sleep too -
            // feeding that back in here caused the actual sleep to oscillate between
            // ~backgroundServiceInterval and ~10ms every other round instead of settling on
            // backgroundServiceInterval, since the previous sleep was effectively counted
            // twice).
            val thisRoundExecutionTime = (endTime - loopStartTime).toInt()

            if (idleBecauseScreenOffAndSilent) {
                // persist any partially counted time before idling so nothing is lost
                // if the process is killed while the device rests
                commitUsedTimeUpdaters()

                // wait lazily; a screen-on or media playback event pokes the wake
                // signal and ends this early
                waitForNextRound(SCREEN_OFF_IDLE_INTERVAL)

                // don't let the idle gap show up as usage time in the next rounds:
                // pretend the previous round just ended. During the gap the screen was
                // off and nothing played, so by definition no usage time passed.
                previousMainLoopEndTime = appLogic.timeApi.getCurrentUptimeInMillis()
                previousMainLogicExecutionTime = 0
            } else {
                val timeToWait = Math.max(MIN_ROUND_GAP, backgroundServiceInterval - thisRoundExecutionTime)
                waitForNextRound(timeToWait)
            }
        }
    }

    private suspend fun syncInstalledAppVersion() {
        val currentAppVersion = BuildConfig.VERSION_CODE
        val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

        if (deviceEntry != null) {
            if (deviceEntry.currentAppVersion != currentAppVersion) {
                ApplyActionUtil.applyAppLogicAction(
                        action = UpdateDeviceStatusAction.empty.copy(
                                newAppVersion = currentAppVersion
                        ),
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                )
            }
        }
    }

    fun syncDeviceStatusAsync() {
        runAsync {
            syncDeviceStatusFast()
        }
    }

    private suspend fun syncDeviceStatusLoop() {
        while (true) {
            appLogic.deviceEntryIfEnabled.waitUntilValueMatches { it != null }

            if (appLogic.platformIntegration.isScreenOn()) {
                syncDeviceStatusSlow()

                appLogic.timeApi.sleep(CHECK_PERMISSION_INTERVAL)
            } else {
                // Permissions and the accessibility/admin status can only be changed
                // interactively, so checking them while the screen is off just burns
                // battery. The main loop additionally triggers syncDeviceStatusAsync()
                // right when the screen turns on (see reportScreenStateToLoop), so
                // manipulation is still detected immediately when the device is used
                // again - this lazy cadence is only a safety net (e.g. for changes
                // made via adb while the screen stayed off).
                appLogic.timeApi.sleep(CHECK_PERMISSION_INTERVAL_SCREEN_OFF)
            }
        }
    }

    private suspend fun checkForceKilled() {
        appLogic.platformIntegration.getExitLog(1).singleOrNull()?.let { item ->
            if (
                item.reason == ExitReason.UserRequest &&
                item.description != null &&
                item.description.startsWith("fully stop ") &&
                item.description.endsWith("by user request")
            ) {
                appLogic.isInitialized.waitUntilValueMatches { it == true }

                try {
                    ApplyActionUtil.applyAppLogicAction(
                        action = UpdateDeviceStatusAction.empty.copy(
                            addedManipulationFlags = ManipulationFlag.USED_FGS_KILLER
                        ),
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                    )
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "could not save a forced kill notification", ex)
                    }
                }
            }
        }
    }

    private val syncDeviceStatusLock = Mutex()

    fun reportDeviceReboot() {
        runAsync {
            val deviceEntry = appLogic.deviceEntry.waitForNullableValue()

            if (deviceEntry?.considerRebootManipulation == true) {
                ApplyActionUtil.applyAppLogicAction(
                        action = UpdateDeviceStatusAction.empty.copy(
                                didReboot = true
                        ),
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                )
            }
        }
    }

    private suspend fun getUpdateDeviceStatusAction(): UpdateDeviceStatusAction {
        val deviceEntry = appLogic.deviceEntry.waitForNullableValue()
        val useStrictChecking = appLogic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.STRICT_OVERLAY_CHECKING).waitForNonNullValue()

        var changes = UpdateDeviceStatusAction.empty

        if (deviceEntry != null) {
            val protectionLevel = appLogic.platformIntegration.getCurrentProtectionLevel()
            val usageStatsPermission = appLogic.platformIntegration.getForegroundAppPermissionStatus()
            val notificationAccess = appLogic.platformIntegration.getNotificationAccessPermissionStatus()
            val overlayPermission = appLogic.platformIntegration.getDrawOverOtherAppsPermissionStatus(useStrictChecking)
            val accessibilityService = appLogic.platformIntegration.isAccessibilityServiceEnabled()
            val qOrLater = AndroidVersion.qOrLater

            if (protectionLevel != deviceEntry.currentProtectionLevel) {
                changes = changes.copy(
                        newProtectionLevel = protectionLevel
                )

                if (protectionLevel == ProtectionLevel.DeviceOwner) {
                    appLogic.platformIntegration.setEnableSystemLockdown(true)
                }
            }

            if (usageStatsPermission != deviceEntry.currentUsageStatsPermission) {
                changes = changes.copy(
                        newUsageStatsPermissionStatus = usageStatsPermission
                )
            }

            if (notificationAccess != deviceEntry.currentNotificationAccessPermission) {
                changes = changes.copy(
                        newNotificationAccessPermission = notificationAccess
                )
            }

            if (overlayPermission != deviceEntry.currentOverlayPermission) {
                changes = changes.copy(
                        newOverlayPermission = overlayPermission
                )
            }

            if (accessibilityService != deviceEntry.accessibilityServiceEnabled) {
                changes = changes.copy(
                        newAccessibilityServiceEnabled = accessibilityService
                )
            }

            if (qOrLater && !deviceEntry.qOrLater) {
                changes = changes.copy(isQOrLaterNow = true)
            }
        }

        return changes
    }

    private suspend fun syncDeviceStatusFast() {
        syncDeviceStatusLock.withLock {
            val changes = getUpdateDeviceStatusAction()

            if (changes != UpdateDeviceStatusAction.empty) {
                ApplyActionUtil.applyAppLogicAction(
                        action = changes,
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                )
            }
        }
    }

    private suspend fun syncDeviceStatusSlow() {
        syncDeviceStatusLock.withLock {
            val changesOne = getUpdateDeviceStatusAction()

            delay(2000)

            val changesTwo = getUpdateDeviceStatusAction()

            if (
                    changesOne != UpdateDeviceStatusAction.empty &&
                    changesOne == changesTwo
            ) {
                ApplyActionUtil.applyAppLogicAction(
                        action = changesOne,
                        appLogic = appLogic,
                        ignoreIfDeviceIsNotConfigured = true
                )
            }
        }
    }

    suspend fun resetTemporarilyAllowedApps() {
        Threads.database.executeAndWait(Runnable {
            appLogic.database.temporarilyAllowedApp().removeAllTemporarilyAllowedAppsSync()
        })
    }

    private suspend fun backupDatabaseLoop() {
        appLogic.timeApi.sleep(1000 * 60 * 5 /* 5 minutes */)

        while (true) {
            DatabaseBackup.with(appLogic.context).tryCreateDatabaseBackupAsync()

            appLogic.timeApi.sleep(1000 * 60 * 60 * 3 /* 3 hours */)
        }
    }

    private suspend fun annoyUserOnManipulationLoop() {
        val shouldAnnoyNow = appLogic.annoyLogic.shouldAnnoyRightNow

        while (true) {
            shouldAnnoyNow.waitUntilValueMatches { it == true }
            appLogic.platformIntegration.showAnnoyScreen()

            // bring into foreground after some time
            withTimeoutOrNull(300 * 1000L) { shouldAnnoyNow.waitUntilValueMatches { it == false } }
        }
    }
}
