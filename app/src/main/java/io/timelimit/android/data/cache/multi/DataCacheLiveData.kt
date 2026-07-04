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

import androidx.lifecycle.LiveData
import io.timelimit.android.async.Threads
import java.util.concurrent.Executor

fun <K, V> DataCacheUserInterface<K, V>.openLive(key: K, executor: Executor): LiveData<V> {
    val cache = this

    return object: LiveData<V>() {
        val listener = object: DataCacheListener<K, V> {
            override fun onElementUpdated(key: K, oldValue: V, newValue: V) {
                postValue(newValue)
            }
        }

        override fun onActive() {
            super.onActive()

            executor.execute {
                val initialValue = cache.openSync(key, listener)

                postValue(initialValue)
            }
        }

        override fun onInactive() {
            super.onInactive()

            executor.execute {
                cache.close(key, listener)
            }
        }
    }
}

fun <K, V> DataCacheUserInterface<K, V>.openLiveAtDatabaseThread(key: K) = openLive(key, Threads.database)