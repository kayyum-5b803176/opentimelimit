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

import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.livedata.*

class AnnoyLogic (val appLogic: AppLogic) {
    // config
    companion object {
        val ENABLE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        private const val LOG_TAG = "AnnoyLogic"

        private const val TEMP_UNBLOCK_DURATION = 1000 * 45L
        private const val TEMP_UNBLOCK_PARENT_DURATION = 1000 * 60 * 10L
        private const val MAX_BLOCK_DURATION = 15

        private fun manualUnblockDelay(counter: Int): Long {
            return if (counter <= 0) 0
            else (counter + 4).coerceAtMost(MAX_BLOCK_DURATION).toLong() * 1000 * 60
        }
    }

    // input: clock
    private fun now() = appLogic.timeApi.getCurrentUptimeInMillis()

    // input: is manipulated (bool)
    private val isManipulated = appLogic.deviceEntryIfEnabled.map { it?.hasActiveManipulationWarning ?: false }
    private val isDeviceOwner = appLogic.deviceEntryIfEnabled.map { it?.currentProtectionLevel == ProtectionLevel.DeviceOwner }
    private val enableAnnoyNow = if (ENABLE) isManipulated.and(isDeviceOwner) else liveDataFromNonNullValue(false)

    private val annoyTempDisabled = MutableLiveData<Boolean>().apply { value = false }
    private val annoyTempDisabledSetFalse: Runnable = Runnable {
        annoyTempDisabled.value = false
        isManipulated.removeObserver(resetTempDisabledObserver)
    }
    private val resetTempDisabledObserver = Observer<Boolean> { isManipulated ->
        if (!isManipulated) {
            Threads.mainThreadHandler.removeCallbacks(annoyTempDisabledSetFalse)
            Threads.mainThreadHandler.post(annoyTempDisabledSetFalse)
        }
    }

    // output: should block right now
    val shouldAnnoyRightNow = enableAnnoyNow.and(annoyTempDisabled.invert())

    // state: block duration
    private var nextManualUnblockTimestamp = now()
    private var manualUnblockCounter = 0

    // output: duration until next manual unblock
    val nextManualUnblockCountdown = liveDataFromFunction {
        (nextManualUnblockTimestamp - now()).coerceAtLeast(0)
    }

    // input: trigger temp unblock (event)
    fun doManualTempUnlock() {
        val now = now()

        if (now < nextManualUnblockTimestamp) return
        if (annoyTempDisabled.value == true) return

        // eventually reset
        if (nextManualUnblockTimestamp + manualUnblockDelay(manualUnblockCounter) < now) {
            manualUnblockCounter = 1
        } else {
            manualUnblockCounter += 1
        }

        nextManualUnblockTimestamp = now + manualUnblockDelay(manualUnblockCounter)

        enableTempDisabled(TEMP_UNBLOCK_DURATION)
        saveManualUnblockCounterAsync()
    }

    // input: trigger temp unblock by parents (event)
    fun doParentTempUnlock() {
        manualUnblockCounter = 0
        nextManualUnblockTimestamp = now()

        enableTempDisabled(TEMP_UNBLOCK_PARENT_DURATION)
        saveManualUnblockCounterAsync()
    }

    // helper functions
    private fun enableTempDisabled(duration: Long) {
        annoyTempDisabled.value = true

        Threads.mainThreadHandler.removeCallbacks(annoyTempDisabledSetFalse)
        Threads.mainThreadHandler.postDelayed(annoyTempDisabledSetFalse, duration)
        isManipulated.observeForever(resetTempDisabledObserver)
    }

    // state saving and restoring
    private fun saveManualUnblockCounterAsync() {
        Threads.mainThreadHandler.removeCallbacks(saveZeroUnblockCounterRunnable)

        Threads.database.execute {
            try {
                appLogic.database.config().setAnoyManualUnblockCounterSync(manualUnblockCounter)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not save unblock counter value", ex)
                }
            }
        }

        eventuallyScheduleResetOfStoredCounter()
    }

    private fun eventuallyScheduleResetOfStoredCounter() {
        if (manualUnblockCounter > 0) {
            val now = now()
            val resetTimestamp = nextManualUnblockTimestamp + manualUnblockDelay(manualUnblockCounter)
            val resetDelay = resetTimestamp - now

            if (resetDelay > 0) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "scheduled counter reset in ${resetDelay/1000} seconds")
                }

                Threads.mainThreadHandler.postDelayed(saveZeroUnblockCounterRunnable, resetDelay)
            }
        }
    }

    private val saveZeroUnblockCounterRunnable = Runnable {
        Threads.database.execute {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "reset counter now")
            }

            try {
                appLogic.database.config().setAnoyManualUnblockCounterSync(0)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not reset saved counter value", ex)
                }
            }
        }
    }

    init {
        runAsync {
            try {
                val counter = Threads.database.executeAndWait {
                    appLogic.database.config().getAnnoyManualUnblockCounter()
                }
                val now = now()

                if (counter > 0) {
                    manualUnblockCounter = counter
                    nextManualUnblockTimestamp = now + manualUnblockDelay(manualUnblockCounter)

                    eventuallyScheduleResetOfStoredCounter()
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not load saved counter", ex)
                }
            }
        }
    }
}