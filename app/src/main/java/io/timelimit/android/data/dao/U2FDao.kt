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
import androidx.room.Query
import io.timelimit.android.data.model.UserU2FKey

@Dao
interface U2FDao {
    @Insert
    fun addKey(key: UserU2FKey)

    @Query("DELETE FROM user_u2f_key WHERE user_id = :parentUserId AND key_handle = :keyHandle AND public_key = :publicKey")
    fun deleteKey(parentUserId: String, keyHandle: ByteArray, publicKey: ByteArray)

    @Query("SELECT * FROM user_u2f_key")
    fun getAllSync(): List<UserU2FKey>

    @Query("SELECT * FROM user_u2f_key WHERE user_id = :userId")
    fun getByUserLive(userId: String): LiveData<List<UserU2FKey>>

    @Query("UPDATe user_u2f_key SET next_counter = :counter + 1 WHERE user_id = :parentUserId AND key_handle = :keyHandle AND public_key = :publicKey AND :counter >= next_counter")
    fun updateCounter(parentUserId: String, keyHandle: ByteArray, publicKey: ByteArray, counter: Long): Int
}