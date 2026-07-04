/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.integration.time

import java.util.*
import kotlin.collections.ArrayList

class DummyTimeApi(var timeStepSizeInMillis: Long): TimeApi() {
    private var currentTime: Long = System.currentTimeMillis()
    private var currentUptime: Long = 0
    private var scheduledActions = Collections.synchronizedList(ArrayList<ScheduledAction>())
    var timeZone = TimeZone.getDefault()

    override fun getCurrentTimeInMillis(): Long {
        return currentTime
    }

    fun setCurrentTimeInMillis(time: Long) {
        this.currentTime = time
    }

    override fun getCurrentUptimeInMillis(): Long {
        return currentUptime
    }

    override fun getSystemTimeZone() = timeZone

    override fun runDelayed(runnable: Runnable, delayInMillis: Long) {
        scheduledActions.add(ScheduledAction(currentUptime + delayInMillis, runnable))
    }

    override fun runDelayedByUptime(runnable: Runnable, delayInMillis: Long) = runDelayed(runnable, delayInMillis)

    override fun cancelScheduledAction(runnable: Runnable) {
        scheduledActions.removeAll { it.action === runnable }
    }

    private fun emulateTimeAtOnce(timeInMillis: Long) {
        if (timeInMillis <= 0) {
            throw IllegalStateException()
        }

        currentTime += timeInMillis
        currentUptime += timeInMillis

        synchronized(scheduledActions) {
            val iterator = scheduledActions.iterator()

            while (iterator.hasNext()) {
                val action = iterator.next()

                if (action.uptime <= currentUptime) {
                    action.action.run()
                    iterator.remove()
                }
            }
        }
    }

    fun emulateTimePassing(timeInMillis: Long) {
        var emulatedTime: Long = 0

        while (emulatedTime < timeInMillis) {
            val missingTime = timeInMillis - emulatedTime

            if (missingTime >= timeStepSizeInMillis) {
                emulateTimeAtOnce(timeStepSizeInMillis)
                emulatedTime += timeStepSizeInMillis
            } else {
                emulateTimeAtOnce(missingTime)
                emulatedTime += missingTime
            }
        }
    }

    private class ScheduledAction (
            val uptime: Long,
            val action: Runnable
    )
}