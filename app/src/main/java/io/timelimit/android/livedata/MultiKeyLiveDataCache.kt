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
package io.timelimit.android.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.ConcurrentHashMap

abstract class LiveDataCache {
    abstract fun reportLoopDone()
    abstract fun removeAllItems()
}

class SingleItemLiveDataCache<T>(private val liveData: LiveData<T>): LiveDataCache() {
    private val dummyObserver = Observer<T> {
        // do nothing
    }

    private var isObserving = false
    private var wasUsed = false

    fun read(): LiveData<T> {
        if (!isObserving) {
            liveData.observeForever(dummyObserver)
            isObserving = true
        }

        wasUsed = true

        return liveData
    }

    override fun removeAllItems() {
        if (isObserving) {
            liveData.removeObserver(dummyObserver)
            isObserving = false
        }
    }

    override fun reportLoopDone() {
        if (isObserving && !wasUsed) {
            removeAllItems()
        }

        wasUsed = false
    }
}

class SingleItemLiveDataCacheWithRequery<T>(private val liveDataCreator: () -> LiveData<T>): LiveDataCache() {
    private val dummyObserver = Observer<T> {
        // do nothing
    }

    private var wasUsed = false
    private var instance: LiveData<T>? = null

    fun read(): LiveData<T> {
        if (instance == null) {
            instance = liveDataCreator()
            instance!!.observeForever(dummyObserver)
        }

        wasUsed = true

        return instance!!
    }

    override fun removeAllItems() {
        if (instance != null) {
            instance!!.removeObserver(dummyObserver)
            instance = null
        }
    }

    override fun reportLoopDone() {
        if (instance != null && !wasUsed) {
            removeAllItems()
        }

        wasUsed = false
    }
}

abstract class MultiKeyLiveDataCache<R, K>: LiveDataCache() {
    class ItemWrapper<R>(val value: LiveData<R>, var used: Boolean)

    private val items = ConcurrentHashMap<K, ItemWrapper<R>>()

    private val dummyObserver = Observer<R> {
        // do nothing
    }

    protected abstract fun createValue(key: K): LiveData<R>

    fun get(key: K): LiveData<R> {
        val oldItem = items[key]

        if (oldItem != null) {
            oldItem.used = true

            return oldItem.value
        } else {
            val newItem = ItemWrapper(createValue(key), true)
            newItem.value.observeForever(dummyObserver)

            items[key] = newItem

            return newItem.value
        }
    }

    override fun reportLoopDone() {
        items.forEach {
            if (it.value.used) {
                it.value.used = false
            } else {
                it.value.value.removeObserver(dummyObserver)
                items.remove(it.key)
            }
        }
    }

    override fun removeAllItems() {
        items.forEach {
            it.value.value.removeObserver(dummyObserver)
        }

        items.clear()
    }
}

class LiveDataCaches(private val caches: Array<LiveDataCache>) {
    fun reportLoopDone() {
        caches.forEach { it.reportLoopDone() }
    }

    fun removeAllItems() {
        caches.forEach { it.removeAllItems() }
    }
}
