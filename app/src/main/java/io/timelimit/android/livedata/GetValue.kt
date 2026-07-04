/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.async.Threads
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun <T> LiveData<T>.waitUntilValueMatches(check: (T?) -> Boolean): T? {
    val liveData = this
    var observer: Observer<T>? = null

    fun removeObserver() {
        val currentObserver = observer

        if (currentObserver != null) {
            liveData.removeObserver(currentObserver)
        }
    }

    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            Threads.mainThreadHandler.post {
                removeObserver()
            }
        }

        observer = Observer { t ->
            if (check(t)) {
                removeObserver()
                continuation.resume(t)
            }
        }

        liveData.observeForever(observer!!)
    }
}

suspend fun <T> LiveData<T>.waitForNullableValue(): T? {
    return waitUntilValueMatches { true }
}

suspend fun <T> LiveData<T>.waitForNonNullValue(): T {
    return waitUntilValueMatches { it != null }!!
}
