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
package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.CategoryApp

@Dao
abstract class CategoryAppDao {
    @Query("SELECT * FROM category_app WHERE category_id IN (:categoryIds) AND package_name = :packageName")
    abstract fun getCategoryApp(categoryIds: List<String>, packageName: String): LiveData<CategoryApp?>

    @Query("SELECT * FROM category_app WHERE category_id IN (:categoryIds) AND package_name = :packageName")
    abstract fun getCategoryAppSync(categoryIds: List<String>, packageName: String): CategoryApp?

    @Query("SELECT * FROM category_app WHERE category_id = :categoryId")
    abstract fun getCategoryApps(categoryId: String): LiveData<List<CategoryApp>>

    @Query("SELECT * FROM category_app WHERE category_id IN (SELECT id FROM category WHERE child_id = :userId)")
    abstract fun getCategoryAppsByUserIdSync(userId: String): List<CategoryApp>

    @Query("SELECT * FROM category_app WHERE category_id IN (:categoryIds)")
    abstract fun getCategoryApps(categoryIds: List<String>): LiveData<List<CategoryApp>>

    @Insert
    abstract fun addCategoryAppsSync(items: Collection<CategoryApp>)

    @Insert
    abstract fun addCategoryAppSync(item: CategoryApp)

    @Query("DELETE FROM category_app WHERE category_id IN (:categoryIds) AND package_name IN (:packageNames)")
    abstract fun removeCategoryAppsSyncByCategoryIds(packageNames: List<String>, categoryIds: List<String>)

    @Query("DELETE FROM category_app WHERE category_id = :categoryId")
    abstract fun deleteCategoryAppsByCategoryId(categoryId: String)

    @Query("SELECT * FROM category_app LIMIT :pageSize OFFSET :offset")
    abstract fun getCategoryAppPageSync(offset: Int, pageSize: Int): List<CategoryApp>
}
