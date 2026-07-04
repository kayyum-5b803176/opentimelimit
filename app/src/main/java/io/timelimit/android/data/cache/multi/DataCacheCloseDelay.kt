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

package io.timelimit.android.data.cache.multi

import android.os.SystemClock
import io.timelimit.android.async.Threads
import java.util.concurrent.atomic.AtomicInteger

fun <K, V> DataCacheUserInterface<K, V>.delayClosingItems(delay: Long): DataCacheUserInterface<K, V> {
    if (delay <= 0) return this

    fun now() = SystemClock.uptimeMillis()

    val handler = Threads.mainThreadHandler
    val parent = this
    val lock = Object()
    // 0 never occurs in user counters, key is only in wipe times or user counters
    val userCounters = mutableMapOf<K, AtomicInteger>()
    val wipeTimes = mutableMapOf<K, Long>()
    var minWipeTime = Long.MAX_VALUE

    lateinit var handleWipingRunnable: Runnable

    fun scheduleWipingRunnable() = synchronized(lock) {
        handler.removeCallbacks(handleWipingRunnable)

        if (minWipeTime != Long.MAX_VALUE) {
            val nextRunDelay = minWipeTime - now()

            handler.postDelayed(handleWipingRunnable, nextRunDelay.coerceAtLeast(10))
        }
    }

    handleWipingRunnable = Runnable {
        synchronized(lock) {
            val now = now()
            var nextWipeTime = Long.MAX_VALUE

            val iterator = wipeTimes.entries.iterator()

            for ((key, time) in iterator) {
                if (time >= now) {
                    parent.close(key, null)
                    iterator.remove()
                } else {
                    nextWipeTime = nextWipeTime.coerceAtMost(time)
                }
            }

            minWipeTime = nextWipeTime

            scheduleWipingRunnable()
        }
    }

    return object: DataCacheUserInterface<K, V> {
        override fun openSync(key: K, listener: DataCacheListener<K, V>?): V {
            val isFirstOpen = synchronized(lock) {
                if (wipeTimes.containsKey(key)) {
                    wipeTimes.remove(key)

                    userCounters[key] = AtomicInteger(1)

                    false
                } else {
                    val counter = userCounters[key]
                            ?: AtomicInteger(0).also { userCounters[key] = it }

                    counter.getAndIncrement() == 0
                }
            }

            // do one more open at the first open
            if (isFirstOpen) {
                parent.openSync(key, null)
            }

            return parent.openSync(key, listener)
        }

        override fun close(key: K, listener: DataCacheListener<K, V>?) {
            synchronized(lock) {
                val counter = userCounters[key]!!
                val isLastClose = counter.decrementAndGet() == 0

                if (isLastClose) {
                    val now = now()
                    val closeTime = now + delay

                    userCounters.remove(key)
                    wipeTimes[key] = closeTime

                    if (closeTime < minWipeTime) {
                        minWipeTime = closeTime
                        scheduleWipingRunnable()
                    }
                }

                isLastClose
            }

            parent.close(key, listener)
        }
    }
}