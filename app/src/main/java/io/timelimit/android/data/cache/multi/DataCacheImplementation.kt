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

import io.timelimit.android.data.cache.TempLock

internal class ListenerHolder<K, V> (val listener: DataCacheListener<K, V>) {
    var closed = false
}

internal class DataCacheElement<K, IV, EV> (var value: IV) {
    var users = 1
    val listeners = mutableListOf<ListenerHolder<K, EV>>()
}

fun <K, IV, EV> DataCacheHelperInterface<K, IV, EV>.createCache(): DataCache<K, EV> {
    val helper = this
    val elements = mutableMapOf<K, DataCacheElement<K, IV, EV>>()
    val lock = Object()
    val tempLock = TempLock()

    fun updateSync(key: K, item: DataCacheElement<K, IV, EV>) {
        if (item.users == 0) return

        val oldValue = item.value
        val newValue = helper.updateItemSync(key, oldValue)

        if (newValue !== oldValue) {
            val listeners = synchronized(lock) {
                val element = elements[key]

                if (element !== item || element.value !== oldValue) {
                    disposeItemFast(key, newValue)
                    return
                } else {
                    element.value = newValue
                    element.listeners.toList()
                }
            }

            listeners.forEach {
                synchronized(it) {
                    if (!it.closed) {
                        it.listener.onElementUpdated(key, helper.prepareForUser(oldValue), helper.prepareForUser(newValue))
                    }
                }
            }
        }
    }

    fun updateSync() = tempLock.withTempLock {
        synchronized(lock) {
            elements.toMap()
        }.forEach {
            updateSync(it.key, it.value)
        }
    }

    fun openSync(key: K, listener: DataCacheListener<K, EV>?): EV = tempLock.withTempLock {
        val oldItemToReturn = synchronized(lock) { elements[key]?.also { oldItem -> oldItem.users++ } }

        if (oldItemToReturn != null) {
            updateSync(key, oldItemToReturn)

            synchronized(lock) {
                if (elements[key] !== oldItemToReturn) {
                    throw IllegalStateException()
                }

                if (listener != null) {
                    if (oldItemToReturn.listeners.find { it.listener === listener } == null) {
                        oldItemToReturn.listeners.add(ListenerHolder(listener))
                    }
                }

                return@withTempLock helper.prepareForUser(oldItemToReturn.value)
            }
        } else {
            val newValue = helper.openItemSync(key)

            synchronized(lock) {
                val currentElement = elements[key]

                if (currentElement == null) {
                    elements[key] = DataCacheElement<K, IV, EV>(newValue).also {
                        if (listener != null) {
                            it.listeners.add(ListenerHolder(listener))
                        }
                    }

                    return@withTempLock helper.prepareForUser(newValue)
                } else {
                    disposeItemFast(key, newValue)

                    currentElement.users++

                    if (listener != null) {
                        if (currentElement.listeners.find { it.listener === listener } == null) {
                            currentElement.listeners.add(ListenerHolder(listener))
                        }
                    }

                    return@withTempLock helper.prepareForUser(currentElement.value)
                }
            }
        }
    }

    fun close(key: K, listener: DataCacheListener<K, EV>?) {
        synchronized(lock) {
            val item = elements[key] ?: throw IllegalStateException()

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

                helper.disposeItemFast(key, item.value)
                elements.remove(key)
            }
        }
    }

    val ownerInterface = object: DataCacheOwnerInterface { override fun updateSync() = helper.wrapOpenOrUpdate { updateSync() } }
    val userInterface = object: DataCacheUserInterface<K, EV> {
        override fun openSync(key: K, listener: DataCacheListener<K, EV>?): EV = helper.wrapOpenOrUpdate { openSync(key, listener) }
        override fun close(key: K, listener: DataCacheListener<K, EV>?) = close(key, listener)
    }

    return DataCache(ownerInterface, userInterface)
}