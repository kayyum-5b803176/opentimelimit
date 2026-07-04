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
package io.timelimit.android.ui.manage.category.blocked_times

object MinuteOfWeekItems: BlockedTimeItems() {
    private const val minutesPerHour = 60
    private const val itemsPerHour = minutesPerHour + 1
    private const val hoursPerDay = 24
    private const val itemsPerDay = 1 + (hoursPerDay * itemsPerHour)
    override val itemsPerWeek = 7 * itemsPerDay
    override val recommendColumns = 10
    override val hasHourHeaders = true
    override val minutesPerTile = 1

    override fun getItemAtPosition(position: Int): Item {
        if (position < 0 || position >= itemsPerWeek) {
            throw IllegalStateException()
        }

        val day = position / itemsPerDay
        val itemInDay = position % itemsPerDay

        if (itemInDay == 0) {
            return DayHeader(day)
        }

        val itemWithinDay = itemInDay - 1
        val hour = itemWithinDay / itemsPerHour
        val itemInHour = itemWithinDay % itemsPerHour

        if (itemInHour == 0) {
            return HourHeader(day, hour)
        }

        val minute = itemInHour - 1

        return MinuteTile(day, hour, minute, 1)
    }

    override fun getDayOfPosition(position: Int): Int {
        return position / itemsPerDay
    }

    override fun getPositionOfItem(item: Item) = when(item) {
        is DayHeader -> item.day * itemsPerDay
        is HourHeader -> item.day * itemsPerDay + 1 + itemsPerHour * item.hour
        is MinuteTile -> item.day * itemsPerDay + 1 + itemsPerHour * item.hour + 1 + item.minute
    }
}
