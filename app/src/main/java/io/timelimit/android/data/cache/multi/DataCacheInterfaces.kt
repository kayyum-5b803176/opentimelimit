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

interface DataCacheUserInterface<K, V> {
    fun openSync(key: K, listener: DataCacheListener<K, V>?): V
    fun close(key: K, listener: DataCacheListener<K, V>?)
}

interface DataCacheOwnerInterface {
    fun updateSync()
}

data class DataCache<K, V>(
        val ownerInterface: DataCacheOwnerInterface,
        val userInterface: DataCacheUserInterface<K, V>
)

interface DataCacheListener<K, V> {
    fun onElementUpdated(key: K, oldValue: V, newValue: V): Unit
}

interface DataCacheHelperInterface<K, IV, EV> {
    fun openItemSync(key: K): IV
    fun updateItemSync(key: K, item: IV): IV
    fun disposeItemFast(key: K, item: IV)
    fun prepareForUser(item: IV): EV
    fun <R> wrapOpenOrUpdate(block: () -> R): R
}