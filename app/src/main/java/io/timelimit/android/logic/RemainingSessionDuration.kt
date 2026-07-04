/*
 * TimeLimit Copyright <C> 2019- 2024 Jonas Lochmann
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

import io.timelimit.android.data.model.SessionDuration
import io.timelimit.android.data.model.TimeLimitRule

object RemainingSessionDuration {
    fun min(a: Long?, b: Long?): Long? {
        if (a == null && b == null) {
            return null
        } else if (a == null) {
            return b
        } else if (b == null) {
            return a
        } else {
            return a.coerceAtMost(b)
        }
    }

    fun getRemainingSessionDuration(
            rules: List<TimeLimitRule>, durationsOfCategory: List<SessionDuration>,
            dayOfWeek: Int, minuteOfDay: Int, timestamp: Long
    ): Long? {
        var result: Long? = null

        rules.forEach { rule ->
            if (
                    rule.sessionDurationLimitEnabled &&
                    rule.dayMask.toInt() and (1 shl dayOfWeek) != 0 &&
                    rule.startMinuteOfDay <= minuteOfDay && rule.endMinuteOfDay >= minuteOfDay
            ) {
                val remaining = durationsOfCategory.filter {
                    it.startMinuteOfDay >= rule.startMinuteOfDay &&
                            it.endMinuteOfDay <= rule.endMinuteOfDay &&
                            it.maxSessionDuration >= rule.sessionDurationMilliseconds &&
                            it.sessionPauseDuration <= rule.sessionPauseMilliseconds &&
                            it.lastUsage + rule.sessionPauseMilliseconds > timestamp
                }.map { durationItem ->
                    (durationItem.maxSessionDuration - durationItem.lastSessionDuration).coerceAtLeast(0)
                }.minOrNull() ?: rule.sessionDurationMilliseconds.toLong()

                result = min(result, remaining)
            }
        }

        return result
    }
}