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

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.*

object RealTimeApi: TimeApi() {
    internal class QueueItem(val runnable: Runnable, val targetUptime: Long): Comparable<QueueItem> {
        override fun compareTo(other: QueueItem): Int = this.targetUptime.compareTo(other.targetUptime)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = PriorityQueue<QueueItem>()

    // why this? because the handler does not use elapsedRealtime
    // this is a workaround
    private val queueProcessor = Runnable {
        synchronized(queue) {
            try {
                val now = getCurrentUptimeInMillis()

                while (true) {
                    val head = queue.peek()

                    if (head == null || head.targetUptime > now) break

                    queue.remove()

                    head.runnable.run()
                }
            } finally {
                scheduleQueue()
            }
        }
    }

    private fun scheduleQueue() {
        synchronized(queue) {
            handler.removeCallbacks(queueProcessor)

            queue.peek()?.let { head ->
                val delay = head.targetUptime - getCurrentUptimeInMillis()

                // at most 5 seconds so that sleeps don't cause trouble
                handler.postDelayed(queueProcessor, delay.coerceAtLeast(0).coerceAtMost(5 * 1000))
            }
        }
    }

    override fun getCurrentTimeInMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun getCurrentUptimeInMillis(): Long {
        return SystemClock.elapsedRealtime()
    }

    override fun runDelayed(runnable: Runnable, delayInMillis: Long) {
        handler.postDelayed(runnable, delayInMillis)
    }

    override fun runDelayedByUptime(runnable: Runnable, delayInMillis: Long) {
        synchronized(queue) {
            queue.add(QueueItem(runnable = runnable, targetUptime = getCurrentUptimeInMillis() + delayInMillis))
            scheduleQueue()
        }
    }

    override fun cancelScheduledAction(runnable: Runnable) {
        handler.removeCallbacks(runnable)

        synchronized(queue) {
            val itemsToRemove = queue.filter { it.runnable === runnable }

            itemsToRemove.forEach { queue.remove(it) }
        }
    }

    override fun getSystemTimeZone() = TimeZone.getDefault()
}