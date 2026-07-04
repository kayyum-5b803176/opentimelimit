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

package io.timelimit.android.data.cache

import android.os.SystemClock

class TempLock {
    private var lastLockTime = 0L
    private val waitLock = Object()
    private val notifier = Object()

    private fun now() = SystemClock.uptimeMillis()

    fun <R> withTempLock(block: () -> R): R {
        val selfStartTime = synchronized(waitLock) {
            synchronized(notifier) {
                val now = now()
                val lastStart = lastLockTime
                val delay = if (lastStart == 0L || lastStart > now) 0 else (1000 - (now - lastStart)).coerceAtLeast(0).coerceAtMost(1000)

                if (delay > 0) {
                    notifier.wait(delay)
                }

                val selfStartTime = now()
                lastLockTime = selfStartTime
                selfStartTime
            }
        }

        try {
            return block()
        } finally {
            synchronized(notifier) {
                if (lastLockTime == selfStartTime) {
                    lastLockTime = 0
                    notifier.notify()
                }
            }
        }
    }
}