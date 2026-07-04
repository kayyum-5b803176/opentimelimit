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

interface SingleItemDataCacheUserInterface<V> {
    fun openSync(listener: SingleItemDataCacheListener<V>?): V
    fun close(listener: SingleItemDataCacheListener<V>?)
}

interface SingleItemDataCacheOwnerInterface {
    fun updateSync()
}

data class SingleItemDataCache<V>(
        val ownerInterface: SingleItemDataCacheOwnerInterface,
        val userInterface: SingleItemDataCacheUserInterface<V>
)

interface SingleItemDataCacheListener<V> {
    fun onElementUpdated(oldValue: V, newValue: V): Unit
}

interface SingleItemDataCacheHelperInterface<IV, EV> {
    fun openItemSync(): IV
    fun updateItemSync(item: IV): IV
    fun disposeItemFast(item: IV)
    fun prepareForUser(item: IV): EV
    fun <R> wrapOpenOrUpdate(block: () -> R): R
}