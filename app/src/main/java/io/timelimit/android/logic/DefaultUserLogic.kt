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
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.derived.DeviceRelatedData
import io.timelimit.android.livedata.*
import io.timelimit.android.sync.actions.SignOutAtDeviceAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultUserLogic(private val appLogic: AppLogic) {
    companion object {
        private const val LOG_TAG = "DefaultUserLogic"

        fun hasAutomaticSignOut(device: DeviceRelatedData): Boolean = device.hasValidDefaultUser && device.deviceEntry.defaultUserTimeout > 0
    }

    private fun defaultUserEntry() = appLogic.deviceEntry.map { device ->
        device?.defaultUser
    }.ignoreUnchanged().switchMap {
        if (it != null)
            appLogic.database.user().getUserByIdLive(it)
        else
            liveDataFromNullableValue(null as User?)
    }
    private fun defaultUserTimeout() = appLogic.deviceEntry.map { it?.defaultUserTimeout ?: 0 }.ignoreUnchanged()

    private val logoutLock = Mutex()

    private var lastScreenOnStatus = false
    private var lastScreenDisableTime = 0L
    private var lastScreenOnSaveTime = 0L
    private var restoredLastScreenOnTime: Long? = null
    private var didRestoreLastDisabledTime = false

    fun reportScreenOn(isScreenOn: Boolean) {
        if (isScreenOn) {
            val now = appLogic.timeApi.getCurrentTimeInMillis()

            if (lastScreenOnSaveTime + 1000 * 30 < now) {
                lastScreenOnSaveTime = now

                Threads.database.submit {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "save last screen on time")
                    }

                    if (restoredLastScreenOnTime == null) {
                        restoredLastScreenOnTime = appLogic.database.config().getLastScreenOnTime()
                    }

                    appLogic.database.config().setLastScreenOnTime(now)
                }
            }
        }

        if (isScreenOn != lastScreenOnStatus) {
            lastScreenOnStatus = isScreenOn

            if (isScreenOn) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "screen was enabled")
                }

                runAsync {
                    logoutLock.withLock {
                        if (lastScreenDisableTime == 0L) {
                            if (!didRestoreLastDisabledTime) {
                                didRestoreLastDisabledTime = true

                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "screen disabling time is not known - try to restore time")
                                }

                                val nowTime = appLogic.timeApi.getCurrentTimeInMillis()
                                val nowUptime = appLogic.timeApi.getCurrentUptimeInMillis()
                                val savedLastScreenOnTime = restoredLastScreenOnTime ?: kotlin.run {
                                    Threads.database.executeAndWait {
                                        restoredLastScreenOnTime = appLogic.database.config().getLastScreenOnTime()
                                    }

                                    restoredLastScreenOnTime!!
                                }

                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "now: $nowTime; uptime: $nowUptime; last screen on time: $savedLastScreenOnTime")
                                }

                                if (savedLastScreenOnTime == 0L) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "no saved value - can not restore")
                                    }
                                } else if (savedLastScreenOnTime > nowTime) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "saved last screen on time is in the future - can not restore")
                                    }
                                } else {
                                    val diffToNow = nowTime - savedLastScreenOnTime
                                    val theoreticallyUptimeValue = nowUptime - diffToNow

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "restored last screen on time: diff to now: ${diffToNow / 1000} s; theoretically uptime: ${theoreticallyUptimeValue / 1000} s")
                                    }

                                    lastScreenDisableTime = theoreticallyUptimeValue
                                }
                            }
                        }

                        if (lastScreenDisableTime != 0L) {
                            val now = appLogic.timeApi.getCurrentUptimeInMillis()
                            val diff = now - lastScreenDisableTime

                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "screen was disabled for ${diff / 1000} seconds")
                            }

                            val defaultUser = defaultUserEntry().waitForNullableValue()

                            if (defaultUser != null) {
                                if (appLogic.deviceEntry.waitForNullableValue()?.currentUserId == defaultUser.id) {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "default user already signed in")
                                    }
                                } else {
                                    val timeout = defaultUserTimeout().waitForNonNullValue()

                                    if (diff >= timeout && timeout != 0) {
                                        if (BuildConfig.DEBUG) {
                                            Log.d(LOG_TAG, "much time - log out")
                                        }

                                        ApplyActionUtil.applyAppLogicAction(
                                                appLogic = appLogic,
                                                action = SignOutAtDeviceAction,
                                                ignoreIfDeviceIsNotConfigured = true
                                        )
                                    } else {
                                        if (BuildConfig.DEBUG) {
                                            Log.d(LOG_TAG, "no reason to log out")
                                        }
                                    }
                                }
                            } else {
                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "has no default user")
                                }
                            }
                        }
                    }
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "screen was disabled")
                }

                lastScreenDisableTime = appLogic.timeApi.getCurrentUptimeInMillis()
            }
        }
    }
}