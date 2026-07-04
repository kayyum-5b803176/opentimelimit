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

sealed class Item
data class DayHeader(val day: Int): Item()
data class HourHeader(val day: Int, val hour: Int): Item()
data class MinuteTile(val day: Int, val hour: Int, val minute: Int, val lengthInMinutes: Int): Item() {
    val minuteOfWeek = (24 * 60 * day) + (60 * hour) + minute
}

abstract class BlockedTimeItems {
    abstract fun getItemAtPosition(position: Int): Item
    abstract fun getDayOfPosition(position: Int): Int
    abstract fun getPositionOfItem(item: Item): Int
    abstract val recommendColumns: Int
    abstract val itemsPerWeek: Int
    abstract val hasHourHeaders: Boolean
    abstract val minutesPerTile: Int
}
