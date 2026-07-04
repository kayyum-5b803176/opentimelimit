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
package io.timelimit.android.util

import android.content.Context
import io.timelimit.android.R

object JoinUtil {
    fun join(parts: List<String>, context: Context) = when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts.first()
        parts.size >= 2 -> {
            val output = StringBuilder()

            parts.forEachIndexed { index, item ->
                output.append(item)

                if (index == parts.size - 2) {
                    output
                            .append(' ')
                            .append(context.getString(R.string.util_join_and))
                            .append(' ')
                } else if (index == parts.size - 1) {
                    // nothing to do, this is the last item
                } else {
                    output.append(", ")
                }
            }

            output.toString()
        }
        else -> throw IllegalStateException()
    }
}
