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
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.data.model.AppActivityTitleAndClassNameItem

@Dao
interface AppActivityDao {
    @Query("SELECT * FROM app_activity LIMIT :pageSize OFFSET :offset")
    fun getAppActivityPageSync(offset: Int, pageSize: Int): List<AppActivity>

    @Query("SELECT * FROM app_activity WHERE device_id IN (:deviceIds)")
    fun getAppActivitiesByDeviceIds(deviceIds: List<String>): LiveData<List<AppActivity>>

    @Query("SELECT DISTINCT activity_class_name, activity_title FROM app_activity WHERE app_package_name = :packageName")
    fun getAppActivitiesByPackageName(packageName: String): LiveData<List<AppActivityTitleAndClassNameItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAppActivitySync(item: AppActivity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAppActivitiesSync(items: List<AppActivity>)

    @Query("DELETE FROM app_activity WHERE device_id = :deviceId AND app_package_name = :packageName AND activity_class_name IN (:activities)")
    fun deleteAppActivitiesSync(deviceId: String, packageName: String, activities: List<String>)

    @Query("DELETE FROM app_activity WHERE device_id IN (:deviceIds)")
    fun deleteAppActivitiesByDeviceIds(deviceIds: List<String>)
}