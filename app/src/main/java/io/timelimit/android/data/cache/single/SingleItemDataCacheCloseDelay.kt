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

package io.timelimit.android.data.cache.single

import io.timelimit.android.async.Threads

fun <V> SingleItemDataCacheUserInterface<V>.delayClosingItem(delay: Long): SingleItemDataCacheUserInterface<V> {
    if (delay <= 0) return this

    val handler = Threads.mainThreadHandler
    val parent = this
    val lock = Object()
    var userCounter = 0

    val doWipeRunnable = Runnable {
        synchronized(lock) {
            if (userCounter == 0) {
                parent.close(null)
            }
        }
    }

    return object: SingleItemDataCacheUserInterface<V> {
        override fun openSync(listener: SingleItemDataCacheListener<V>?): V {
            val isFirstOpen = synchronized(lock) {
                if (userCounter++ == 0) {
                    handler.removeCallbacks(doWipeRunnable)

                    true
                } else {
                    false
                }
            }

            if (isFirstOpen) { openSync(null) }

            return parent.openSync(listener)
        }

        override fun close(listener: SingleItemDataCacheListener<V>?) = synchronized(lock) {
            if (userCounter <= 0) { throw IllegalStateException() }

            parent.close(listener)

            if (--userCounter == 0) {
                handler.postDelayed(doWipeRunnable, delay)
            }
        }
    }
}