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

package io.timelimit.android.logic

import android.widget.Toast
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.runAsyncExpectForever
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

data class WatchdogLogic(private val logic: AppLogic) {
    init {
        runAsyncExpectForever {
            val boolean = AtomicBoolean(false)
            val runnable = Runnable {
                logic.database.runInUnobservedTransaction {
                    logic.database.config().getOwnDeviceIdSync()
                }

                boolean.compareAndSet(false, true)
            }

            while (true) {
                Threads.database.execute(runnable)

                for (i in 1..15) {
                    delay(1000)
                }

                if (!boolean.getAndSet(false)) {
                    Toast.makeText(logic.context, "TimeLimit: watchdog triggered", Toast.LENGTH_SHORT).show()

                    while (true) {
                        delay(3000)
                        logic.platformIntegration.restartApp()
                    }
                }
            }
        }
    }
}