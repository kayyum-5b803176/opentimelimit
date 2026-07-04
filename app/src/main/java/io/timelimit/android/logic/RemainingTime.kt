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

import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UsedTimeItem

data class RemainingTime(val includingExtraTime: Long, val default: Long) {
    val hasRemainingTime = includingExtraTime > 0
    val usingExtraTime = includingExtraTime > 0 && default == 0L

    init {
        if (includingExtraTime < 0 || default < 0) {
            throw IllegalStateException("time is < 0")
        }

        if (includingExtraTime < default) {
            throw IllegalStateException("extra time < default time")
        }
    }

    companion object {
        fun min(a: RemainingTime?, b: RemainingTime?): RemainingTime? = if (a == null) {
            b
        } else if (b == null) {
            a
        } else {
            RemainingTime(
                    includingExtraTime = Math.min(a.includingExtraTime, b.includingExtraTime),
                    default = Math.min(a.default, b.default)
            )
        }

        fun getRulesRelatedToDay(dayOfWeek: Int, minuteOfDay: Int, rules: List<TimeLimitRule>): List<TimeLimitRule> {
            return rules.filter {
                ((it.dayMask.toInt() and (1 shl dayOfWeek)) != 0) &&
                        minuteOfDay >= it.startMinuteOfDay && minuteOfDay <= it.endMinuteOfDay
            }
        }

        fun getRemainingTime(dayOfWeek: Int, minuteOfDay: Int, usedTimes: List<UsedTimeItem>, rules: List<TimeLimitRule>, extraTime: Long, firstDayOfWeekAsEpochDay: Int): RemainingTime? {
            if (extraTime < 0) {
                throw IllegalStateException("extra time < 0")
            }

            val relatedRules = getRulesRelatedToDay(dayOfWeek, minuteOfDay, rules)
            val withoutExtraTime = getRemainingTime(usedTimes, relatedRules, false, firstDayOfWeekAsEpochDay, dayOfWeek)
            val withExtraTime = getRemainingTime(usedTimes, relatedRules, true, firstDayOfWeekAsEpochDay, dayOfWeek)

            if (withoutExtraTime == null && withExtraTime == null) {
                // no rules
                return null
            } else if (withoutExtraTime != null && withExtraTime != null) {
                // with rules for extra time
                val additionalTimeWithExtraTime = withExtraTime - withoutExtraTime

                if (additionalTimeWithExtraTime < 0) {
                    throw IllegalStateException("additional time with extra time < 0")
                }

                return RemainingTime(
                        includingExtraTime = withoutExtraTime + Math.min(extraTime, additionalTimeWithExtraTime),
                        default = withoutExtraTime
                )
            } else if (withoutExtraTime != null) {
                // without rules for extra time
                return RemainingTime(
                        includingExtraTime = withoutExtraTime + extraTime,
                        default = withoutExtraTime
                )
            } else {
                throw IllegalStateException()
            }
        }

        private fun getRemainingTime(usedTimes: List<UsedTimeItem>, relatedRules: List<TimeLimitRule>, assumeMaximalExtraTime: Boolean, firstDayOfWeekAsEpochDay: Int, dayOfWeek: Int): Long? {
            return relatedRules.filter { (!assumeMaximalExtraTime) || it.applyToExtraTimeUsage }.map { rule ->
                val usedTime = getUsedTime(
                    usedTimes = usedTimes,
                    rule = rule,
                    firstDayOfWeekAsEpochDay = firstDayOfWeekAsEpochDay,
                    dayOfWeekForDailyRule = if (rule.perDay) dayOfWeek else null
                )

                val maxTime = rule.maximumTimeInMillis

                (maxTime - usedTime).coerceAtLeast(0)
            }.minOrNull()
        }

        fun getUsedTime(usedTimes: List<UsedTimeItem>, rule: TimeLimitRule, firstDayOfWeekAsEpochDay: Int, dayOfWeekForDailyRule: Int?): Long {
            val usedTimeByDay = longArrayOf(0, 0, 0, 0, 0, 0, 0)

            usedTimes.forEach { usedTimeItem ->
                val doesWeekMatch = usedTimeItem.dayOfEpoch >= firstDayOfWeekAsEpochDay && usedTimeItem.dayOfEpoch <= firstDayOfWeekAsEpochDay + 6

                if (doesWeekMatch) {
                    val usedTimeItemDayOfWeek = usedTimeItem.dayOfEpoch - firstDayOfWeekAsEpochDay
                    val doesDayMaskMatch = (rule.dayMask.toInt() and (1 shl usedTimeItemDayOfWeek)) != 0

                    val dayMatch = rule.perDay or doesDayMaskMatch
                    val hourMatch = rule.startMinuteOfDay <= usedTimeItem.startTimeOfDay && rule.endMinuteOfDay >= usedTimeItem.endTimeOfDay

                    if (dayMatch && hourMatch)
                        usedTimeByDay[usedTimeItemDayOfWeek] = usedTimeByDay[usedTimeItemDayOfWeek].coerceAtLeast(usedTimeItem.usedMillis)
                }
            }

            return if (dayOfWeekForDailyRule == null) usedTimeByDay.sum()
            else usedTimeByDay[dayOfWeekForDailyRule]
        }
    }
}