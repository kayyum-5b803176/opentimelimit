/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.integration.platform.android

import android.annotation.TargetApi
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.QueryAppTitleCache
import io.timelimit.android.logic.blockingreason.AppBaseHandling
import io.timelimit.android.logic.blockingreason.CategoryItselfHandling
import kotlinx.coroutines.delay

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class NotificationListener: NotificationListenerService() {
    companion object {
        private const val LOG_TAG = "NotificationListenerLog"
        private val SUPPORTS_HIDING_ONGOING_NOTIFICATIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(this) }
    private val notificationManager: NotificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val queryAppTitleCache: QueryAppTitleCache by lazy { QueryAppTitleCache(appLogic.platformIntegration) }
    private val lastOngoingNotificationHidden = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()

        NotificationChannels.createNotificationChannels(notificationManager, this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, sbn.packageName)
        }

        runAsync {
            val result = shouldRemoveNotification(sbn)

            if (result is ShouldBlockNotificationResult.Yes) {
                val success = try {
                    if (sbn.isOngoing && SUPPORTS_HIDING_ONGOING_NOTIFICATIONS) {
                        // only snooze for 5 seconds to show it again soon
                        snoozeNotification(sbn.key, 5000)

                        if (!lastOngoingNotificationHidden.add(sbn.packageName)) {
                            // skip showing again a notification that it was blocked
                            return@runAsync
                        }
                    } else {
                        if (result.delay > 0) {
                            delay(result.delay.coerceAtMost(Category.MAX_NOTIFICATION_BLOCK_DELAY))
                        }

                        cancelNotification(sbn.key)
                    }

                    true
                } catch (ex: SecurityException) {
                    // this occurs when the notification access is revoked
                    // while this function is running

                    false
                }

                notificationManager.notify(
                        sbn.packageName,
                        NotificationIds.NOTIFICATION_BLOCKED,
                        NotificationCompat.Builder(this@NotificationListener, NotificationChannels.BLOCKED_NOTIFICATIONS_NOTIFICATION)
                                .setDefaults(NotificationCompat.DEFAULT_ALL)
                                .setSmallIcon(R.drawable.ic_stat_block)
                                .setContentTitle(
                                        if (success)
                                            getString(R.string.notification_filter_not_blocked_title)
                                        else
                                            getString(R.string.notification_filter_blocking_failed_title)
                                )
                                .setContentText(
                                        queryAppTitleCache.query(sbn.packageName) +
                                                " - " +
                                                when (result.reason) {
                                                    BlockingReason.NotPartOfAnCategory -> getString(R.string.lock_reason_short_no_category)
                                                    BlockingReason.TemporarilyBlocked -> getString(R.string.lock_reason_short_temporarily_blocked)
                                                    BlockingReason.TimeOver -> getString(R.string.lock_reason_short_time_over)
                                                    BlockingReason.TimeOverExtraTimeCanBeUsedLater -> getString(R.string.lock_reason_short_time_over)
                                                    BlockingReason.BlockedAtThisTime -> getString(R.string.lock_reason_short_blocked_time_area)
                                                    BlockingReason.NotificationsAreBlocked -> getString(R.string.lock_reason_short_notification_blocking)
                                                    BlockingReason.BatteryLimit -> getString(R.string.lock_reason_short_battery_limit)
                                                    BlockingReason.SessionDurationLimit -> getString(R.string.lock_reason_short_session_duration)
                                                    BlockingReason.MissingRequiredNetwork -> getString(R.string.lock_reason_short_missing_required_network)
                                                    BlockingReason.MissingNetworkCheckPermission -> getString(R.string.lock_reason_short_missing_network_check_permission)
                                                    BlockingReason.ForbiddenNetwork -> getString(R.string.lock_reason_short_forbidden_network)
                                                    BlockingReason.None -> throw IllegalStateException()
                                                }
                                )
                                .setLocalOnly(true)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .build()
                )
            } else {
                if (sbn.isOngoing) {
                    lastOngoingNotificationHidden.remove(sbn.packageName)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)

        // not interesting but required for old android versions
    }

    private suspend fun shouldRemoveNotification(sbn: StatusBarNotification): ShouldBlockNotificationResult {
        if (sbn.packageName == packageName) {
            return ShouldBlockNotificationResult.No
        }

        if (sbn.isOngoing && (!SUPPORTS_HIDING_ONGOING_NOTIFICATIONS)) {
            return ShouldBlockNotificationResult.No
        }

        val deviceAndUserRelatedData = Threads.database.executeAndWait {
            appLogic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()
        }

        return if (deviceAndUserRelatedData?.userRelatedData?.user?.type != UserType.Child) {
            ShouldBlockNotificationResult.No
        } else {
            val isSystemImageApp = appLogic.platformIntegration.isSystemImageApp(sbn.packageName)

            val appHandling = AppBaseHandling.calculate(
                    foregroundAppPackageName = sbn.packageName,
                    foregroundAppActivityName = null,
                    pauseCounting = false,
                    pauseForegroundAppBackgroundLoop = false,
                    userRelatedData = deviceAndUserRelatedData.userRelatedData,
                    deviceRelatedData = deviceAndUserRelatedData.deviceRelatedData,
                    isSystemImageApp = isSystemImageApp
            )

            val blockCategories = appHandling.getCategories(AppBaseHandling.GetCategoriesPurpose.Blocking)

            if (appHandling is AppBaseHandling.BlockDueToNoCategory && !isSystemImageApp) {
                ShouldBlockNotificationResult.Yes(
                    reason = BlockingReason.NotPartOfAnCategory,
                    delay = 0
                )
            } else if (blockCategories.iterator().hasNext()) {
                val battery = appLogic.platformIntegration.getBatteryStatus()
                val now = appLogic.timeApi.getCurrentTimeInMillis()
                val networkId = if (appHandling.needsNetworkId) appLogic.platformIntegration.getCurrentNetworkId() else null

                val categoryHandlings = blockCategories.map { categoryId ->
                    CategoryItselfHandling.calculate(
                            categoryRelatedData = deviceAndUserRelatedData.userRelatedData.categoryById[categoryId]!!,
                            user = deviceAndUserRelatedData.userRelatedData,
                            batteryStatus = battery,
                            timeInMillis = now,
                            currentNetworkId = networkId
                    )
                }

                categoryHandlings.find { it.shouldBlockActivities }?.let { handling ->
                    return ShouldBlockNotificationResult.Yes(
                        reason = handling.activityBlockingReason,
                        delay = 0
                    )
                }

                (categoryHandlings.map { it.blockAllNotifications }.maxOrNull() ?: CategoryItselfHandling.BlockAllNotifications.No)
                    .let { blockAllNotifications ->
                        if (blockAllNotifications is CategoryItselfHandling.BlockAllNotifications.Yes) {
                            ShouldBlockNotificationResult.Yes(
                                reason = BlockingReason.NotificationsAreBlocked,
                                delay = blockAllNotifications.delay
                            )
                        } else {
                            ShouldBlockNotificationResult.No
                        }
                    }
            } else {
                ShouldBlockNotificationResult.No
            }
        }
    }

    private sealed class ShouldBlockNotificationResult {
        object No: ShouldBlockNotificationResult()
        data class Yes(val reason: BlockingReason, val delay: Long): ShouldBlockNotificationResult()
    }
}