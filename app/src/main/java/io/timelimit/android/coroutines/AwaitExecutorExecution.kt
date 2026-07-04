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
package io.timelimit.android.coroutines

import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun Executor.executeAndWait(runnable: Runnable) {
    suspendCoroutine<Void?> {
        this.execute {
            try {
                runnable.run()

                it.resume(null)
            } catch (ex: Exception) {
                it.resumeWithException(ex)
            }
        }
    }
}

suspend fun <R> Executor.executeAndWait(function: () -> R) = suspendCoroutine<R> {
    this.execute {
        try {
            it.resume(function())
        } catch (ex: Exception) {
            it.resumeWithException(ex)
        }
    }
}
