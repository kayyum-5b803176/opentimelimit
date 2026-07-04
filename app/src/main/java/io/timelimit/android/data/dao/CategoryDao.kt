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
import androidx.room.*
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskAdapter
import io.timelimit.android.data.model.Category

@Dao
abstract class CategoryDao {
    @Query("SELECT * FROM category WHERE child_id = :childId")
    abstract fun getCategoriesByChildId(childId: String): LiveData<List<Category>>

    @Query("SELECT * FROM category WHERE child_id = :childId AND id = :categoryId")
    abstract fun getCategoryByChildIdAndId(childId: String, categoryId: String): LiveData<Category?>

    @Query("SELECT * FROM category WHERE id = :categoryId")
    abstract fun getCategoryByIdSync(categoryId: String): Category?

    @Query("SELECT * FROM category WHERE child_id = :childId")
    abstract fun getCategoriesByChildIdSync(childId: String): List<Category>

    @Query("DELETE FROM category WHERE id = :categoryId")
    abstract fun deleteCategory(categoryId: String)

    @Insert
    abstract fun addCategory(category: Category)

    @Query("UPDATE category SET title = :newTitle WHERE id = :categoryId")
    abstract fun updateCategoryTitle(categoryId: String, newTitle: String)

    @Query("UPDATE category SET extra_time = :newExtraTime, extra_time_day = :extraTimeDay WHERE id = :categoryId")
    abstract fun updateCategoryExtraTime(categoryId: String, newExtraTime: Long, extraTimeDay: Int)

    @Query("UPDATE category SET extra_time = MAX(0, extra_time - :removedExtraTime) WHERE id = :categoryId")
    abstract fun subtractCategoryExtraTime(categoryId: String, removedExtraTime: Int)

    @TypeConverters(ImmutableBitmaskAdapter::class)
    @Query("UPDATE category SET blocked_times = :blockedMinutesInWeek WHERE id = :categoryId")
    abstract fun updateCategoryBlockedTimes(categoryId: String, blockedMinutesInWeek: ImmutableBitmask)

    @Query("UPDATE category SET temporarily_blocked = :blocked, temporarily_blocked_end_time = :endTime WHERE id = :categoryId")
    abstract fun updateCategoryTemporarilyBlocked(categoryId: String, blocked: Boolean, endTime: Long)

    @Query("SELECT * FROM category LIMIT :pageSize OFFSET :offset")
    abstract fun getCategoryPageSync(offset: Int, pageSize: Int): List<Category>

    @Query("SELECT id, child_id, temporarily_blocked, temporarily_blocked_end_time FROM category")
    abstract fun getAllCategoriesShortInfo(): LiveData<List<CategoryShortInfo>>

    @Query("UPDATE category SET parent_category_id = :parentCategoryId WHERE id = :categoryId")
    abstract fun updateParentCategory(categoryId: String, parentCategoryId: String)

    @Update
    abstract fun updateCategorySync(category: Category)

    // if there is no category, then the result is null
    // Room converts null to 0 si it works
    @Query("SELECT MAX(sort) + 1 FROM category WHERE child_id = :childId")
    abstract fun getNextCategorySortKeyByChildId(childId: String): Int

    @Query("UPDATE category SET sort = :sort WHERE id = :categoryId")
    abstract fun updateCategorySorting(categoryId: String, sort: Int)
}

data class CategoryShortInfo(
        @ColumnInfo(name = "child_id")
        val childId: String,
        @ColumnInfo(name = "id")
        val categoryId: String,
        @ColumnInfo(name = "temporarily_blocked")
        val temporarilyBlocked: Boolean,
        @ColumnInfo(name = "temporarily_blocked_end_time")
        val temporarilyBlockedEndTime: Long
)
