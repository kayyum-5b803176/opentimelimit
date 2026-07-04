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
package io.timelimit.android.date

import java.util.*

fun getMinuteOfWeek(timeInMillis: Long, timeZone: TimeZone): Int {
    val calendar = CalendarCache.getCalendar()

    calendar.firstDayOfWeek = Calendar.MONDAY

    calendar.timeZone = timeZone
    calendar.timeInMillis = timeInMillis

    val dayOfWeek = getDayOfWeek(calendar)
    val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
    val minuteOfHour = calendar.get(Calendar.MINUTE)

    return minuteOfHour + 60 * (hourOfDay + 24 * dayOfWeek)
}
