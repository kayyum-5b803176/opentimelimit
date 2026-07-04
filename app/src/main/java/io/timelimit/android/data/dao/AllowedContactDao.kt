/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
import io.timelimit.android.data.model.AllowedContact

@Dao
interface AllowedContactDao {
    @Query("SELECT * FROM allowed_contact LIMIT :pageSize OFFSET :offset")
    fun getAllowedContactPageSync(offset: Int, pageSize: Int): List<AllowedContact>

    @Query("SELECT * FROM allowed_contact")
    fun getAllowedContactsLive(): LiveData<List<AllowedContact>>

    @Insert
    fun addContactSync(item: AllowedContact)

    @Query("DELETE FROM allowed_contact WHERE id = :id")
    fun removeContactSync(id: Int)
}
