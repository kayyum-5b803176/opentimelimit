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
import io.timelimit.android.data.model.UserKey

@Dao
interface UserKeyDao {
    @Query("SELECT * FROM user_key WHERE `key` = :publicKey")
    fun findUserKeyByPublicKeySync(publicKey: ByteArray): UserKey?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun addUserKeySync(key: UserKey)

    @Query("SELECT * FROM user_key LIMIT :pageSize OFFSET :offset")
    fun getUserKeyPageSync(offset: Int, pageSize: Int): List<UserKey>

    @Query("SELECT * FROM  user_key WHERE user_id = :userId")
    fun getUserKeyByUserIdLive(userId: String): LiveData<UserKey?>

    @Query("DELETE FROM user_key WHERE user_id = :userId")
    fun deleteUserKeySync(userId: String)

    @Query("UPDATE user_key SET last_use = :timestamp WHERE `key` = :key")
    fun updateKeyTimestamp(key: ByteArray, timestamp: Long)

    @Query("SELECT COUNT(*) FROM user_key")
    fun countLive(): LiveData<Long>
}
