/*
 * Open TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
import androidx.room.Update
import io.timelimit.android.data.model.User

@Dao
abstract class UserDao {
    @Query("SELECT * from user WHERE id = :userId")
    abstract fun getUserByIdLive(userId: String): LiveData<User?>

    @Query("SELECT * from user WHERE id = :userId")
    abstract suspend fun getUserByIdCoroutine(userId: String): User?

    @Query("SELECT * from user WHERE id = :userId AND type = \"child\"")
    abstract fun getChildUserByIdLive(userId: String): LiveData<User?>

    @Query("SELECT * from user WHERE id = :userId AND type = \"parent\"")
    abstract fun getParentUserByIdLive(userId: String): LiveData<User?>

    @Query("SELECT * from user WHERE id = :userId")
    abstract fun getUserByIdSync(userId: String): User?

    @Insert
    abstract fun addUserSync(user: User)

    @Query("SELECT * FROM user ORDER BY type DESC, name ASC")
    abstract fun getAllUsersLive(): LiveData<List<User>>

    @Query("SELECT * FROM user WHERE type = \"parent\"")
    abstract fun getParentUsersLive(): LiveData<List<User>>

    @Query("SELECT * FROM user WHERE type = \"parent\"")
    abstract fun getParentUsersSync(): List<User>

    @Query("DELETE FROM user WHERE id IN (:userIds)")
    abstract fun deleteUsersByIds(userIds: List<String>)

    @Update
    abstract fun updateUserSync(user: User)

    @Query("UPDATE user SET disable_limits_until = :timestamp WHERE id = :childId AND type = \"child\"")
    abstract fun updateDisableChildUserLimitsUntil(childId: String, timestamp: Long): Int

    @Query("SELECT * FROM user LIMIT :pageSize OFFSET :offset")
    abstract fun getUserPageSync(offset: Int, pageSize: Int): List<User>

    @Query("UPDATE user SET category_for_not_assigned_apps = :categoryId WHERE id = :childId")
    abstract fun updateCategoryForUnassignedApps(childId: String, categoryId: String)

    @Query("UPDATE user SET category_for_not_assigned_apps = \"\" WHERE category_for_not_assigned_apps = :categoryId")
    abstract fun removeAsCategoryForUnassignedApps(categoryId: String)

    @Query("UPDATE user SET timezone = :timezone WHERE id = :userId")
    abstract fun updateUserTimezone(userId: String, timezone: String)
}
