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

import io.timelimit.android.data.cache.TempLock

internal class ListenerHolder<V> (val listener: SingleItemDataCacheListener<V>) {
    var closed = false
}

internal class DataCacheElement<IV, EV> (var value: IV) {
    var users = 1
    val listeners = mutableListOf<ListenerHolder<EV>>()
}

fun <IV, EV> SingleItemDataCacheHelperInterface<IV, EV>.createCache(): SingleItemDataCache<EV> {
    val helper = this
    val lock = Object()
    val tempLock = TempLock()

    var element: DataCacheElement<IV, EV>? = null

    fun updateSync() = tempLock.withTempLock {
        val currentElement = synchronized(lock) { element } ?: return@withTempLock

        val oldValue = currentElement.value
        val newValue = helper.updateItemSync(currentElement.value)

        if (oldValue === newValue) return@withTempLock

        val listeners = synchronized(lock) {
            val newElement = element

            if (newElement !== currentElement || newElement.value !== oldValue) {
                disposeItemFast(newValue)
                return@withTempLock
            } else {
                disposeItemFast(newElement.value)
                newElement.value = newValue

                newElement.listeners.toList()
            }
        }

        listeners.forEach {
            synchronized(it) {
                if (!it.closed) {
                    it.listener.onElementUpdated(helper.prepareForUser(oldValue), helper.prepareForUser(newValue))
                }
            }
        }
    }

    fun openSync(listener: SingleItemDataCacheListener<EV>?): EV {
        val oldItemToReturn = synchronized(lock) {
            element?.also { oldItem -> oldItem.users++ }
        }

        if (oldItemToReturn != null) {
            updateSync()

            synchronized(lock) {
                if (oldItemToReturn !== element) {
                    throw IllegalStateException()
                }

                if (listener != null) {
                    if (oldItemToReturn.listeners.find { it.listener === listener } == null) {
                        oldItemToReturn.listeners.add(ListenerHolder(listener))
                    }
                }
            }

            return helper.prepareForUser(oldItemToReturn.value)
        } else {
            val value = helper.openItemSync()

            synchronized(lock) {
                val currentElement = element

                if (currentElement == null) {
                    element = DataCacheElement<IV, EV>(value).also {
                        if (listener != null) {
                            it.listeners.add(ListenerHolder(listener))
                        }
                    }

                    return helper.prepareForUser(value)
                } else {
                    disposeItemFast(value)

                    currentElement.users++

                    if (listener != null) {
                        if (currentElement.listeners.find { it.listener === listener } == null) {
                            currentElement.listeners.add(ListenerHolder(listener))
                        }
                    }

                    return helper.prepareForUser(currentElement.value)
                }
            }
        }
    }

    fun close(listener: SingleItemDataCacheListener<EV>?) {
        synchronized(lock) {
            val item = element ?: throw IllegalStateException()

            val iterator = item.listeners.iterator()

            for (listenerItem in iterator) {
                if (listenerItem.listener === listener) {
                    synchronized(listenerItem) {
                        listenerItem.closed = true
                        iterator.remove()
                    }
                }
            }

            item.users--

            if (item.users < 0) {
                throw IllegalStateException()
            }

            if (item.users == 0) {
                if (item.listeners.isNotEmpty()) {
                    throw IllegalStateException()
                }

                helper.disposeItemFast(item.value)
                element = null
            }
        }
    }

    val ownerInterface = object: SingleItemDataCacheOwnerInterface { override fun updateSync() = helper.wrapOpenOrUpdate { updateSync() } }
    val userInterface = object: SingleItemDataCacheUserInterface<EV> {
        override fun openSync(listener: SingleItemDataCacheListener<EV>?): EV = helper.wrapOpenOrUpdate { openSync(listener) }
        override fun close(listener: SingleItemDataCacheListener<EV>?) = close(listener)
    }

    return SingleItemDataCache(ownerInterface, userInterface)
}