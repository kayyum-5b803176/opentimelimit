/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
import androidx.room.*
import io.timelimit.android.data.model.CategoryTimeWarning

@Dao
interface CategoryTimeWarningDao {
    @Query("SELECT * FROM category_time_warning")
    fun getAllItemsSync(): List<CategoryTimeWarning>

    @Query("SELECT * FROM category_time_warning WHERE category_id = :categoryId")
    fun getItemsByCategoryIdSync(categoryId: String): List<CategoryTimeWarning>

    @Query("SELECT * FROM category_time_warning WHERE category_id = :categoryId")
    fun getItemsByCategoryIdLive(categoryId: String): LiveData<List<CategoryTimeWarning>>

    @Insert
    fun insertItemsSync(items: List<CategoryTimeWarning>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertItemIgnoreConflictSync(item: CategoryTimeWarning)

    @Query("DELETE FROM category_time_warning WHERE category_id = :categoryId AND minutes = :minutes")
    fun deleteItem(categoryId: String, minutes: Int)
}