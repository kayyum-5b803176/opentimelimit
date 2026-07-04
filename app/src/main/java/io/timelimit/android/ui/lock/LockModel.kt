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

package io.timelimit.android.ui.lock

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.TemporarilyAllowedApp
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.derived.DeviceAndUserRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.integration.platform.NetworkId
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.BlockingLevel
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.blockingreason.AppBaseHandling
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache
import io.timelimit.android.logic.blockingreason.CategoryItselfHandling

class LockModel(application: Application): AndroidViewModel(application) {
    private val packageAndActivityNameLiveInternal = MutableLiveData<Pair<String, String?>>()
    private var didInit = false

    private val logic = DefaultAppLogic.with(application)
    private val deviceAndUserRelatedData: LiveData<DeviceAndUserRelatedData?> = logic.database.derivedDataDao().getUserAndDeviceRelatedDataLive()
    private val batteryStatus: LiveData<BatteryStatus> = logic.platformIntegration.getBatteryStatusLive()
    private val realNetworkIdLive: LiveData<NetworkId> = liveDataFromFunction { logic.platformIntegration.getCurrentNetworkId() }
    private val needsNetworkIdLive = MutableLiveData<Boolean>().apply { value = false }
    private val networkIdLive: LiveData<NetworkId?> by lazy { needsNetworkIdLive.switchMap { needsNetworkId ->
        if (needsNetworkId) realNetworkIdLive as LiveData<NetworkId?> else liveDataFromNullableValue(null as NetworkId?)
    }.ignoreUnchanged() }
    private val handlingCache = CategoryHandlingCache()

    val title: String? get() = logic.platformIntegration.getLocalAppTitle(packageAndActivityNameLiveInternal.value!!.first)
    val icon: Drawable? get() = logic.platformIntegration.getAppIcon(packageAndActivityNameLiveInternal.value!!.first)
    val packageAndActivityNameLive: LiveData<Pair<String, String?>> = packageAndActivityNameLiveInternal

    fun init(packageName: String, activityName: String?) {
        if (didInit) return

        packageAndActivityNameLiveInternal.value = packageName to activityName
    }

    val enableAlternativeDurationSelection = logic.database.config().getEnableAlternativeDurationSelectionAsync()

    val content: LiveData<LockscreenContent> = object: MediatorLiveData<LockscreenContent>() {
        private val updateRunnable = Runnable { update() }
        private val timeModificationListener: () -> Unit = { update() }

        init {
            addSource(deviceAndUserRelatedData) { update() }
            addSource(batteryStatus) { update() }
            addSource(networkIdLive) { update() }
            addSource(packageAndActivityNameLiveInternal) { update() }
        }

        private fun update() {
            val deviceAndUserRelatedData = deviceAndUserRelatedData.value ?: return
            val batteryStatus = batteryStatus.value ?: return
            val networkId = networkIdLive.value
            val (packageName, activityName) = packageAndActivityNameLiveInternal.value ?: return
            val timeInMillis = logic.timeApi.getCurrentTimeInMillis()

            if (deviceAndUserRelatedData.userRelatedData?.user?.type != UserType.Child) {
                value = LockscreenContent.Close; return
            }

            val appBaseHandling = AppBaseHandling.calculate(
                    foregroundAppPackageName = packageName,
                    foregroundAppActivityName = activityName,
                    deviceRelatedData = deviceAndUserRelatedData.deviceRelatedData,
                    userRelatedData = deviceAndUserRelatedData.userRelatedData,
                    pauseForegroundAppBackgroundLoop = false,
                    pauseCounting = false,
                    isSystemImageApp = logic.platformIntegration.isSystemImageApp(packageName)
            )

            val needsNetworkId = appBaseHandling.needsNetworkId

            if (needsNetworkId != needsNetworkIdLive.value) {
                needsNetworkIdLive.value = needsNetworkId
            }

            if (needsNetworkId && networkId == null) return

            handlingCache.reportStatus(
                    user = deviceAndUserRelatedData.userRelatedData,
                    batteryStatus = batteryStatus,
                    timeInMillis = timeInMillis,
                    currentNetworkId = networkId
            )

            val blockingCategories = appBaseHandling.getCategories(AppBaseHandling.GetCategoriesPurpose.Blocking)

            if (blockingCategories.iterator().hasNext()) {
                val categoryHandlings = blockingCategories.map { handlingCache.get(it) }
                val blockingHandling = categoryHandlings.find { it.shouldBlockActivities }

                value = if (blockingHandling == null) LockscreenContent.Close else LockscreenContent.Blocked.BlockedCategory(
                        deviceAndUserRelatedData = deviceAndUserRelatedData,
                        blockingHandling = blockingHandling,
                        level = appBaseHandling.level,
                        userRelatedData = deviceAndUserRelatedData.userRelatedData,
                        appPackageName = packageName,
                        appActivityName = activityName
                ).also { scheduleUpdate((blockingHandling.dependsOnMaxTime - timeInMillis)) }
            } else if (appBaseHandling is AppBaseHandling.BlockDueToNoCategory) {
                value = LockscreenContent.Blocked.BlockDueToNoCategory(
                        userRelatedData = deviceAndUserRelatedData.userRelatedData,
                        deviceId = deviceAndUserRelatedData.deviceRelatedData.deviceEntry.id,
                        enableActivityLevelBlocking = deviceAndUserRelatedData.deviceRelatedData.deviceEntry.enableActivityLevelBlocking,
                        appPackageName = packageName,
                        appActivityName = activityName
                )
            } else {
                value = LockscreenContent.Close; return
            }
        }

        private fun scheduleUpdate(delay: Long) {
            logic.timeApi.cancelScheduledAction(updateRunnable)
            logic.timeApi.runDelayedByUptime(updateRunnable, delay)
        }

        private fun unscheduleUpdate() {
            logic.timeApi.cancelScheduledAction(updateRunnable)
        }

        override fun onActive() {
            super.onActive()

            logic.realTimeLogic.registerTimeModificationListener(timeModificationListener)

            update()
        }

        override fun onInactive() {
            super.onInactive()

            unscheduleUpdate()
            logic.realTimeLogic.unregisterTimeModificationListener(timeModificationListener)
        }
    }

    val missingNetworkIdPermission = networkIdLive.map { it is NetworkId.MissingPermission }

    val osClockInMillis = liveDataFromFunction { logic.timeApi.getCurrentTimeInMillis() }

    private val categoryIdForTasks = content.map {
        if (it is LockscreenContent.Blocked.BlockedCategory && it.blockingHandling.activityBlockingReason == BlockingReason.TimeOver)
            it.blockedCategoryId
        else null
    }.ignoreUnchanged()

    val blockedCategoryTasks = categoryIdForTasks.switchMap { categoryId ->
        if (categoryId != null)
            logic.database.childTasks().getTasksByCategoryId(categoryId)
        else liveDataFromNonNullValue(emptyList())
    }

    fun allowAppTemporarily() {
        // this accesses the database directly because it is not synced
        Threads.database.submit {
            try {
                logic.database.runInTransaction {
                    logic.database.config().getOwnDeviceIdSync()?.let { deviceId ->
                        logic.database.temporarilyAllowedApp().addTemporarilyAllowedAppSync(TemporarilyAllowedApp(
                                packageName = packageAndActivityNameLiveInternal.value!!.first
                        ))
                    }
                }
            } catch (ex: SQLiteConstraintException) {
                // ignore this
                //
                // this happens when touching that option more than once very fast
                // or if the device is under load
            }
        }
    }

    fun setEnablePickerMode(enable: Boolean) {
        Threads.database.execute {
            logic.database.config().setEnableAlternativeDurationSelectionSync(enable)
        }
    }
}

sealed class LockscreenContent {
    object Close: LockscreenContent()

    sealed class Blocked: LockscreenContent() {
        abstract val userRelatedData: UserRelatedData
        abstract val appPackageName: String
        abstract val appActivityName: String?
        abstract val enableActivityLevelBlocking: Boolean
        abstract val reason: BlockingReason
        abstract val level: BlockingLevel

        class BlockedCategory(
                val deviceAndUserRelatedData: DeviceAndUserRelatedData,
                val blockingHandling: CategoryItselfHandling,
                override val level: BlockingLevel,
                override val userRelatedData: UserRelatedData,
                override val appPackageName: String,
                override val appActivityName: String?
        ): Blocked() {
            val appCategoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title
            override val reason = blockingHandling.activityBlockingReason
            val deviceId = deviceAndUserRelatedData.deviceRelatedData.deviceEntry.id
            val userId = userRelatedData.user.id
            val timeZone = userRelatedData.user.timeZone
            val blockedCategoryId = blockingHandling.createdWithCategoryRelatedData.category.id
            val deviceRelatedData = deviceAndUserRelatedData.deviceRelatedData
            override val enableActivityLevelBlocking = deviceAndUserRelatedData.deviceRelatedData.deviceEntry.enableActivityLevelBlocking
        }

        class BlockDueToNoCategory(
                override val userRelatedData: UserRelatedData,
                val deviceId: String,
                override val enableActivityLevelBlocking: Boolean,
                override val appPackageName: String,
                override val appActivityName: String?
        ): Blocked() {
            override val level: BlockingLevel = BlockingLevel.App
            override val reason: BlockingReason = BlockingReason.NotPartOfAnCategory
        }
    }
}