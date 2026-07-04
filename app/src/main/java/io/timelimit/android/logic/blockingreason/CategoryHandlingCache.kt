/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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

package io.timelimit.android.logic.blockingreason

import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.integration.platform.NetworkId

class CategoryHandlingCache {
    private val cachedItems = mutableMapOf<String, CategoryItselfHandling>()

    private lateinit var user: UserRelatedData
    private lateinit var batteryStatus: BatteryStatus
    private var timeInMillis: Long = 0
    private var currentNetworkId: NetworkId? = null

    fun reportStatus(
            user: UserRelatedData,
            batteryStatus: BatteryStatus,
            timeInMillis: Long,
            currentNetworkId: NetworkId?
    ) {
        this.user = user
        this.batteryStatus = batteryStatus
        this.timeInMillis = timeInMillis
        this.currentNetworkId = currentNetworkId

        val iterator = cachedItems.iterator()

        for (item in iterator) {
            val category = user.categoryById[item.key]

            if (
                    category == null ||
                    !item.value.isValid(
                            categoryRelatedData = category,
                            user = user,
                            batteryStatus = batteryStatus,
                            timeInMillis = timeInMillis,
                            currentNetworkId = currentNetworkId
                    )
            ) {
                iterator.remove()
            }
        }
    }

    fun get(categoryId: String): CategoryItselfHandling {
        if (!cachedItems.containsKey(categoryId)) {
            cachedItems[categoryId] = calculate(categoryId)
        }

        return cachedItems[categoryId]!!
    }

    private fun calculate(categoryId: String): CategoryItselfHandling = CategoryItselfHandling.calculate(
            categoryRelatedData = user.categoryById[categoryId]!!,
            user = user,
            batteryStatus = batteryStatus,
            timeInMillis = timeInMillis,
            currentNetworkId = currentNetworkId
    )
}