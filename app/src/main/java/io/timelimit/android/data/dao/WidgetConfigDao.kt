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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.timelimit.android.data.model.WidgetConfig

@Dao
interface WidgetConfigDao {
    @Query("SELECT * FROM widget_config")
    fun queryAll(): List<WidgetConfig>

    @Query("SELECT * FROM widget_config WHERE widget_id = :widgetId")
    fun queryByWidgetId(widgetId: Int): WidgetConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(config: WidgetConfig)

    @Query("DELETE FROM widget_config WHERE widget_id IN (:widgetIds)")
    fun deleteByWidgetIds(widgetIds: IntArray)

    @Query("DELETE FROM widget_config")
    fun deleteAll()
}