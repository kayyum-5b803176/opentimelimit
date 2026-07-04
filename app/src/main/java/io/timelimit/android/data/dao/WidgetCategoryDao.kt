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
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.timelimit.android.data.model.WidgetCategory

@Dao
interface WidgetCategoryDao {
    @Query("DELETE FROM widget_category WHERE widget_id = :widgetId")
    fun deleteByWidgetId(widgetId: Int)

    @Query("DELETE FROM widget_category WHERE widget_id IN (:widgetIds)")
    fun deleteByWidgetIds(widgetIds: IntArray)

    @Query("DELETE FROM widget_category WHERE widget_id = :widgetId AND category_id IN (:categoryIds)")
    fun deleteByWidgetIdAndCategoryIds(widgetId: Int, categoryIds: List<String>)

    @Query("DELETE FROM widget_category")
    fun deleteAll()

    @Query("SELECT * FROM widget_category")
    fun queryLive(): LiveData<List<WidgetCategory>>

    @Query("SELECT category_id FROM widget_category WHERE widget_id = :widgetId")
    fun queryByWidgetIdSync(widgetId: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(items: List<WidgetCategory>)
}