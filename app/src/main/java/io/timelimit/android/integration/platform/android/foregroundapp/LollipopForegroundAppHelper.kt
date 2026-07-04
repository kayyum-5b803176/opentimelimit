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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.annotation.TargetApi
import android.app.usage.UsageEvents
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class LollipopForegroundAppHelper(context: Context) : UsageStatsForegroundAppHelper(context) {
    companion object {
        private const val LOG_TAG = "LollipopForegroundApp"
        private const val QUERY_TIME_TOLERANCE = 2500L

        val enableMultiAppDetectionGeneral = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    private var lastQueryTime: Long = 0
    private val currentForegroundApps = mutableSetOf<ForegroundApp>()
    private var currentForegroundAppsSnapshot: Set<ForegroundApp> = emptySet()
    private var lastHandledEventTime: Long = 0
    private val event = UsageEvents.Event()
    private var callsSinceLastActivityExistsCheck = 0
    private var lastEnableMultiAppDetection = false

    @Throws(SecurityException::class)
    override suspend fun getForegroundApps(queryInterval: Long, experimentalFlags: Long): Set<ForegroundApp> {
        if (getPermissionStatus() == RuntimePermissionStatus.NotGranted) {
            throw SecurityException()
        }

        val enableMultiAppDetection = experimentalFlags and ExperimentalFlags.MULTI_APP_DETECTION == ExperimentalFlags.MULTI_APP_DETECTION
        val effectiveEnableMultiAppDetection = enableMultiAppDetection && enableMultiAppDetectionGeneral

        backgroundThread.executeAndWait {
            val now = System.currentTimeMillis()
            var currentForegroundAppsModified = false

            if (lastQueryTime > now || queryInterval >= 1000 * 60 * 60 * 24 /* 1 day */ || lastEnableMultiAppDetection != effectiveEnableMultiAppDetection) {
                // if the time went backwards, forget everything
                lastQueryTime = 0
                lastHandledEventTime = 0
                currentForegroundApps.clear(); currentForegroundAppsModified = true
                lastEnableMultiAppDetection = effectiveEnableMultiAppDetection
            }

            val queryStartTime = if (lastQueryTime == 0L) {
                // query data for last 7 days
                now - 1000 * 60 * 60 * 24 * 7
            } else {
                // query data since last query
                lastQueryTime - Math.max(queryInterval, QUERY_TIME_TOLERANCE)
            }

            usageStatsManager.queryEvents(queryStartTime, now + QUERY_TIME_TOLERANCE)?.let { usageEvents ->
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)

                    if (event.timeStamp >= lastHandledEventTime) {
                        lastHandledEventTime = event.timeStamp

                        if (event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN || event.eventType == UsageEvents.Event.DEVICE_STARTUP) {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "device reboot => reset")
                            }

                            currentForegroundApps.clear(); currentForegroundAppsModified = true
                        } else if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "resume ${event.packageName}:${event.className}")
                            }

                            if (effectiveEnableMultiAppDetection) {
                                val app = ForegroundApp(event.packageName, event.className)

                                if (!doesActivityExist(app)) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... ignore because it can not run")
                                    }

                                    continue
                                }

                                currentForegroundApps.add(app); currentForegroundAppsModified = true
                            } else {
                                val currentForegroundApp = currentForegroundApps.singleOrNull()
                                val matchingForegroundApp = currentForegroundApp != null && currentForegroundApp.packageName == event.packageName && currentForegroundApp.activityName == event.className

                                if (!matchingForegroundApp) {
                                    currentForegroundApps.clear(); currentForegroundApps.add(ForegroundApp(event.packageName, event.className))

                                    currentForegroundAppsModified = true
                                }
                            }
                        } else if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                            if (effectiveEnableMultiAppDetection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                val app = ForegroundApp(event.packageName, event.className)

                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "pause ${event.packageName}:${event.className}")
                                }

                                val currentlyRunning = currentForegroundApps.contains(app)

                                if (!currentlyRunning) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... was not running")
                                    }
                                } else {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "... stopped last instance")
                                    }

                                    currentForegroundApps.remove(app); currentForegroundAppsModified = true
                                }
                            }
                        }
                    }
                }
            }

            if (callsSinceLastActivityExistsCheck++ > 256) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "do activity exists check")
                }

                callsSinceLastActivityExistsCheck = 0

                val iterator = currentForegroundApps.iterator()

                while (iterator.hasNext()) {
                    val app = iterator.next()

                    if (!doesActivityExist(app)) {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "...remove $app")
                        }

                        iterator.remove()
                        currentForegroundAppsModified = true
                    }
                }
            }

            if (currentForegroundAppsModified) {
                currentForegroundAppsSnapshot = currentForegroundApps.toSet()
            }

            lastQueryTime = now
        }

        return currentForegroundAppsSnapshot
    }

    // Android 9 (and maybe older versions too) do not report pausing Apps if they are disabled while running
    private fun doesActivityExist(app: ForegroundApp) = doesActivityExistSimple(app) || doesActivityExistAsAlias(app)

    private fun doesActivityExistSimple(app: ForegroundApp) = app.activityName != null && try {
        packageManager.getActivityInfo(ComponentName(app.packageName, app.activityName), 0).isEnabled
    } catch (ex: PackageManager.NameNotFoundException) {
        false
    }

    private fun doesActivityExistAsAlias(app: ForegroundApp) = try {
        packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES).activities.find {
            it.enabled && it.targetActivity == app.activityName
        } != null
    } catch (ex: PackageManager.NameNotFoundException) {
        false
    }
}
