/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.data.model.AppRecommendationConverter

@Dao
@TypeConverters(
        AppRecommendationConverter::class
)
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAppsSync(apps: Collection<App>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAppSync(app: App)

    @Query("DELETE FROM app WHERE package_name IN (:packageNames)")
    fun removeAppsByPackageNamesSync(packageNames: List<String>)

    @Query("SELECT * FROM app")
    fun getApps(): LiveData<List<App>>

    @Query("SELECT * FROM app WHERE package_name = :packageName")
    fun getAppsByPackageName(packageName: String): LiveData<List<App>>

    @Query("SELECT * FROM app LIMIT :pageSize OFFSET :offset")
    fun getAppPageSync(offset: Int, pageSize: Int): List<App>

    @Query("SELECT * FROM app WHERE recommendation = :recommendation")
    fun getAppsByRecommendationLive(recommendation: AppRecommendation): LiveData<List<App>>
}
