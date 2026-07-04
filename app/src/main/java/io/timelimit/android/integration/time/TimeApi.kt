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
package io.timelimit.android.integration.time

import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class TimeApi {
    // normal clock - can be modified by the user at any time
    abstract fun getCurrentTimeInMillis(): Long
    // clock which starts at 0 at boot
    abstract fun getCurrentUptimeInMillis(): Long
    // function to run something delayed at the UI Thread
    abstract fun runDelayed(runnable: Runnable, delayInMillis: Long)
    abstract fun runDelayedByUptime(runnable: Runnable, delayInMillis: Long)
    abstract fun cancelScheduledAction(runnable: Runnable)
    suspend fun sleep(timeInMillis: Long) = suspendCoroutine<Void?> {
        runDelayed(Runnable {
            it.resume(null)
        }, timeInMillis)
    }
    abstract fun getSystemTimeZone(): TimeZone
}
