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

package io.timelimit.android.extensions

object MinuteOfDay {
    const val MIN = 0
    const val MAX = 24 * 60 - 1
    const val LENGTH = 24 * 60

    fun isValid(value: Int) = value >= MIN && value <= MAX

    fun format(minuteOfDay: Int): String {
        if (minuteOfDay < MIN || minuteOfDay > MAX) {
            return "???"
        } else {
            val hour = minuteOfDay / 60
            val minute = minuteOfDay % 60

            val hourString = hour.toString()
            val minuteString = minute.toString().padStart(2, '0')

            return "$hourString:$minuteString"
        }
    }
}