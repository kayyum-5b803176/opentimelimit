/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.data.extensions

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import io.timelimit.android.data.model.User
import io.timelimit.android.livedata.ignoreUnchanged
import java.util.*

fun User?.getTimezone(): TimeZone {
    return if (this != null) {
        TimeZone.getTimeZone(this.timeZone)
    } else {
        null
    } ?: TimeZone.getDefault()
}

fun LiveData<User?>.mapToTimezone() = this.map { it.getTimezone() }.ignoreUnchanged()
