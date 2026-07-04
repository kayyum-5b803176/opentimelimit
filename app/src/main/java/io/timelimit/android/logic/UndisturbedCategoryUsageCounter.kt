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
package io.timelimit.android.logic

class UndisturbedCategoryUsageCounter {
    private val data = mutableMapOf<String, Long>()

    fun reset() {
        data.clear()
    }

    fun report(uptime: Long, categories: Set<String>) {
        removeUnknown(categories)
        addNewCategories(categories, uptime)
    }

    fun getRecentlyStartedCategories(uptime: Long): Set<String> {
        return data.filterValues { it >= uptime - 5000 }.keys
    }

    private fun removeUnknown(currentCategories: Set<String>) {
        val iterator = data.iterator()

        while (iterator.hasNext()) {
            if (!currentCategories.contains(iterator.next().key)) {
                iterator.remove()
            }
        }
    }

    private fun addNewCategories(categories: Set<String>, uptime: Long) {
        categories.forEach { categoryId ->
            if (!data.containsKey(categoryId)) {
                data[categoryId] = uptime
            }
        }
    }
}