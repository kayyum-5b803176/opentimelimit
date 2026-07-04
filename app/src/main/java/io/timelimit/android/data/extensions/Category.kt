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
package io.timelimit.android.data.extensions

import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.derived.CategoryRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData

fun UserRelatedData.sortedCategories(): List<Pair<Int, CategoryRelatedData>> {
    val result = mutableListOf<Pair<Int, CategoryRelatedData>>()
    val presorted = this.categories.sortedBy { it.category.sort }

    fun appendSortedInternal(categoryId: String, level: Int) {
        val category = this.categoryById[categoryId]!!

        result.add(level to category)

        val childCategories = presorted.filter { it.category.parentCategoryId == categoryId }

        childCategories.forEach { appendSortedInternal(it.category.id, level + 1) }
    }

    presorted.forEach { category ->
        val hasParentCategory = this.categoryById.containsKey(category.category.parentCategoryId)

        if (!hasParentCategory) {
            appendSortedInternal(category.category.id, 0)
        }
    }

    return result.toList()
}

fun UserRelatedData.getChildCategories(categoryId: String): Set<String> {
    if (!this.categoryById.containsKey(categoryId)) {
        return emptySet()
    }

    val result = mutableSetOf<String>()
    val processedCategoryIds = mutableSetOf<String>()

    fun handle(currentCategoryId: String) {
        if (!processedCategoryIds.add(currentCategoryId)) return

        val childCategories = this.categories.filter { it.category.parentCategoryId == currentCategoryId }

        childCategories.forEach { childCategory ->
            result.add(childCategory.category.id)
            handle(childCategory.category.id)
        }
    }; handle(categoryId)

    return result
}

fun UserRelatedData.getCategoryWithParentCategories(startCategoryId: String): Set<String> {
    val startCategory = categoryById[startCategoryId]!!
    val categoryIds = mutableSetOf(startCategoryId)

    var currentCategory: CategoryRelatedData? = categoryById[startCategory.category.parentCategoryId]

    while (currentCategory != null && categoryIds.add(currentCategory.category.id)) {
        currentCategory = categoryById[currentCategory.category.parentCategoryId]
    }

    return categoryIds
}

fun List<Category>.getCategoryWithParentCategories(startCategoryId: String): Set<String> {
    val categoryById = this.associateBy { it.id }

    val startCategory = categoryById[startCategoryId]!!
    val categoryIds = mutableSetOf(startCategoryId)

    var currentCategory: Category? = categoryById[startCategory.parentCategoryId]

    while (currentCategory != null && categoryIds.add(currentCategory.id)) {
        currentCategory = categoryById[currentCategory.parentCategoryId]
    }

    return categoryIds
}

fun List<Category>.getChildCategories(categoryId: String): Set<String> {
    if (this.find { it.id == categoryId } != null) {
        return emptySet()
    }

    val result = mutableSetOf<String>()
    val processedCategoryIds = mutableSetOf<String>()

    fun handle(currentCategoryId: String) {
        if (!processedCategoryIds.add(currentCategoryId)) return

        val childCategories = this.filter { it.parentCategoryId == currentCategoryId }

        childCategories.forEach { childCategory ->
            result.add(childCategory.id)
            handle(childCategory.id)
        }
    }; handle(categoryId)

    return result
}