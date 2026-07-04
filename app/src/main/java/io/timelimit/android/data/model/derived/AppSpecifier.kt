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
package io.timelimit.android.data.model.derived

import kotlin.text.StringBuilder

data class AppSpecifier(val packageName: String, val activityName: String?) {
    companion object {
        fun decode(input: String): AppSpecifier {
            val activityIndex = input.indexOf(':')

            val packageName = if (activityIndex == -1) input else input.substring(0, activityIndex)
            val activityName = if (activityIndex == -1) null else input.substring(activityIndex + 1)

            return AppSpecifier(
                packageName = packageName,
                activityName = activityName
            )
        }
    }

    init {
        if (packageName.indexOf(':') != -1) {
            throw InvalidValueException()
        }
    }

    fun encode(): String = StringBuilder().let { builder ->
        builder.append(packageName)

        if (activityName != null) {
            builder.append(':').append(activityName)
        }

        builder.trimToSize()
        builder.toString()
    }

    class InvalidValueException: RuntimeException()
}