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
package io.timelimit.android.date

import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import java.util.*

data class DateInTimezone(val dayOfWeek: Int, val dayOfEpoch: Int, val localDate: LocalDate) {
    val firstDayOfWeekAsEpochDay get() = dayOfEpoch - dayOfWeek

    companion object {
        fun convertDayOfWeek(dayOfWeek: Int) = when(dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> throw IllegalStateException()
        }

        fun convertDayOfWeek(dayOfWeek: DayOfWeek) = when(dayOfWeek) {
            DayOfWeek.MONDAY -> 0
            DayOfWeek.TUESDAY -> 1
            DayOfWeek.WEDNESDAY -> 2
            DayOfWeek.THURSDAY -> 3
            DayOfWeek.FRIDAY -> 4
            DayOfWeek.SATURDAY -> 5
            DayOfWeek.SUNDAY -> 6
            else -> throw IllegalStateException()
        }

        fun getLocalDate(timeInMillis: Long, timeZone: TimeZone): LocalDate {
            val calendar = CalendarCache.getCalendar()

            calendar.firstDayOfWeek = Calendar.MONDAY

            calendar.timeZone = timeZone
            calendar.timeInMillis = timeInMillis

            return LocalDate.of(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun newInstance(localDate: LocalDate): DateInTimezone = DateInTimezone(
                dayOfEpoch = localDate.toEpochDay().toInt(),
                dayOfWeek = convertDayOfWeek(localDate.dayOfWeek),
                localDate = localDate
        )

        fun newInstance(timeInMillis: Long, timeZone: TimeZone) = newInstance(getLocalDate(timeInMillis, timeZone))
    }
}
