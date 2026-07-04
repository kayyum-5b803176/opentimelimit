/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.util

import android.content.Context
import io.timelimit.android.R

object DayNameUtil {
    fun formatDayNameMask(mask: Byte, context: Context): String {
        val dayNames = context.resources.getStringArray(R.array.days_of_week_array)
        val dayGroups = mutableListOf<String>()

        fun isDaySelected(index: Int): Boolean = mask.toInt() and (1 shl index) != 0

        var i = 0

        while (i < 7) {
            if (isDaySelected(i++)) {
                val start = i - 1
                while (isDaySelected(i++)) {/* intentionally blank */}
                val end = i - 2

                if (start == end) dayGroups.add(dayNames[start])
                else if (start + 1 == end) { dayGroups.add(dayNames[start]); dayGroups.add(dayNames[start + 1]) }
                else dayGroups.add(context.getString(R.string.util_day_from_to, dayNames[start], dayNames[end]))
            }
        }

        return if (dayGroups.isEmpty()) context.getString(R.string.util_day_no_day)
            else JoinUtil.join(dayGroups, context)
    }
}