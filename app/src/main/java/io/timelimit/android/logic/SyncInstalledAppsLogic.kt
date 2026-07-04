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

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.actions.*
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncInstalledAppsLogic(val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "SyncInstalledAppsLogic"
    }

    private val doSyncLock = Mutex()
    private var requestSync = MutableLiveData<Boolean>().apply { value = false }

    private fun requestSync() {
        requestSync.value = true
    }

    init {
        appLogic.platformIntegration.installedAppsChangeListener = Runnable { requestSync() }
        appLogic.deviceEntryIfEnabled.map { device ->
            device?.let { DeviceState(
                id = device.id,
                enableActivityLevelBlocking = device.enableActivityLevelBlocking,
                isDeviceOwner = device.currentProtectionLevel == ProtectionLevel.DeviceOwner
            ) }
        }.ignoreUnchanged().observeForever { requestSync() }

        runAsyncExpectForever { syncLoop() }
    }

    private suspend fun syncLoop() {
        // wait a moment before the first sync
        appLogic.timeApi.sleep(15 * 1000)

        while (true) {
            requestSync.waitUntilValueMatches { it == true }
            requestSync.value = false

            try {
                doSyncNow()

                // maximal 1 time per 5 seconds
                appLogic.timeApi.sleep(5 * 1000)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not sync installed app list", ex)
                }

                Toast.makeText(appLogic.context, R.string.background_logic_toast_sync_apps, Toast.LENGTH_SHORT).show()

                appLogic.timeApi.sleep(45 * 1000)
                requestSync.value = true
            }
        }
    }

    private suspend fun doSyncNow() {
        doSyncLock.withLock {
            val deviceEntry = appLogic.deviceEntryIfEnabled.waitForNullableValue()

            if (deviceEntry == null) {
                return
            }

            val deviceId = deviceEntry.id

            val currentlyInstalledApps = getCurrentApps()

            run {
                val currentlySaved = appLogic.database.app().getApps().waitForNonNullValue().associateBy { app -> app.packageName }

                // skip all items for removal which are still saved locally
                val itemsToRemove = HashMap(currentlySaved)
                currentlyInstalledApps.forEach { (packageName, _) -> itemsToRemove.remove(packageName) }

                // only add items which are not the same locally
                val itemsToAdd = currentlyInstalledApps.filter { (packageName, app) -> currentlySaved[packageName] != app }

                // save the changes
                if (itemsToRemove.isNotEmpty()) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = RemoveInstalledAppsAction(packageNames = itemsToRemove.keys.toList()),
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }

                if (itemsToAdd.isNotEmpty()) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = AddInstalledAppsAction(
                                    apps = itemsToAdd.map { (_, app) ->

                                        InstalledApp(
                                                packageName = app.packageName,
                                                title = app.title,
                                                recommendation = app.recommendation,
                                                isLaunchable = app.isLaunchable
                                        )
                                    }
                            ),
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }

            run {
                fun buildKey(activity: AppActivity) = "${activity.appPackageName}:${activity.activityClassName}"

                val currentlyInstalled = if (deviceEntry.enableActivityLevelBlocking)
                    Threads.backgroundOSInteraction.executeAndWait {
                        val realActivities = appLogic.platformIntegration.getLocalAppActivities(deviceId = deviceId)
                        val dummyActivities = currentlyInstalledApps.keys.map { packageName ->
                            AppActivity(
                                deviceId = deviceId,
                                appPackageName = packageName,
                                activityClassName = DummyApps.ACTIVITY_BACKGROUND_AUDIO,
                                title = appLogic.context.getString(R.string.dummy_app_activity_audio)
                            )
                        }

                        val allActivities = realActivities + dummyActivities

                        allActivities.associateBy { buildKey(it) }
                    }
                else
                    emptyMap()

                val currentlySaved = appLogic.database.appActivity().getAppActivitiesByDeviceIds(deviceIds = listOf(deviceId)).waitForNonNullValue().associateBy { buildKey(it) }

                // skip all items for removal which are still saved locally
                val itemsToRemove = HashMap(currentlySaved)
                currentlyInstalled.forEach { (packageName, _) -> itemsToRemove.remove(packageName) }

                // only add items which are not the same locally
                val itemsToAdd = currentlyInstalled.filter { (packageName, app) -> currentlySaved[packageName] != app }

                // save the changes
                if (itemsToRemove.isNotEmpty() or itemsToAdd.isNotEmpty()) {
                    ApplyActionUtil.applyAppLogicAction(
                            action = UpdateAppActivitiesAction(
                                    removedActivities = itemsToRemove.map { it.value.appPackageName to it.value.activityClassName },
                                    updatedOrAddedActivities = itemsToAdd.map { item ->
                                        AppActivityItem(
                                                packageName = item.value.appPackageName,
                                                className = item.value.activityClassName,
                                                title = item.value.title
                                        )
                                    }
                            ),
                            appLogic = appLogic,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }
        }
    }

    private suspend fun getCurrentApps(): Map<String, App> {
        val currentlyInstalled = Threads.backgroundOSInteraction.executeAndWait {
            appLogic.platformIntegration.getLocalApps().associateBy { app -> app.packageName }
        }

        val featureDummyApps = appLogic.platformIntegration.getFeatures().map {
            DummyApps.forFeature(
                id = it.id,
                title = it.title
            )
        }.associateBy { it.packageName }

        return currentlyInstalled + featureDummyApps
    }

    internal data class DeviceState(
        val id: String,
        val enableActivityLevelBlocking: Boolean,
        val isDeviceOwner: Boolean
    )
}
