/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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

import io.timelimit.android.integration.time.TimeApi

class DayChangeTracker (private val timeApi: TimeApi, private val longDuration: Long) {
    private var lastDayOfEpoch = -1
    private var lastDayOfEpochChange = -1L
    private var lastReportedDayChangeDuration = -1L

    fun reportDayChange(newDay: Int): DayChange {
        val uptime = timeApi.getCurrentUptimeInMillis()

        return if (lastDayOfEpoch != newDay) {
            lastDayOfEpochChange = uptime
            lastDayOfEpoch = newDay
            lastReportedDayChangeDuration = 0

            DayChange.Now
        } else {
            val newDayChangeDuration = uptime - lastDayOfEpochChange

            try {
                if (newDayChangeDuration >= longDuration && lastReportedDayChangeDuration < longDuration) {
                    DayChange.NowSinceLongerTime
                } else {
                    DayChange.No
                }
            } finally {
                lastReportedDayChangeDuration = newDayChangeDuration
            }
        }
    }

    enum class DayChange {
        No,
        Now,
        NowSinceLongerTime
    }

    fun reset() {
        lastDayOfEpoch = -1
        lastDayOfEpochChange = -1
        lastReportedDayChangeDuration = -1
    }
}
