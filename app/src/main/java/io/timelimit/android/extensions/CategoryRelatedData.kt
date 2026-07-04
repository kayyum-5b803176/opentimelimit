/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.extensions

import io.timelimit.android.data.model.derived.CategoryRelatedData

fun CategoryRelatedData.nextBlockedMinuteOfWeek(firstMinuteToCheck: Int): Int? {
    val byBitmask = this.category.blockedMinutesInWeek.dataNotToModify.nextSetBit(firstMinuteToCheck).let { if (it < 0) Int.MAX_VALUE else it }
    val byRules = this.rules.map {
        if (it.likeBlockedTimeArea) {
            for (day in 0..7) {
                if ((it.dayMask.toInt() and (1 shl day)) == 0) continue // no matching day

                val dayOffset = day * MinuteOfDay.LENGTH
                val startMinuteOfWeek = dayOffset + it.startMinuteOfDay
                val endMinuteOfWeek = dayOffset + it.endMinuteOfDay

                if (firstMinuteToCheck in startMinuteOfWeek..endMinuteOfWeek) {
                    // within the range
                    return@map firstMinuteToCheck
                }

                if (firstMinuteToCheck < startMinuteOfWeek) {
                    return@map startMinuteOfWeek
                }
            }
        }

        Int.MAX_VALUE
    }.minOrNull() ?: Int.MAX_VALUE

    val max = byRules.coerceAtMost(byBitmask)

    return if (max == Int.MAX_VALUE) null else max
}