/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import androidx.room.*
import io.timelimit.android.data.model.SessionDuration

@Dao
interface SessionDurationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addSessionDurationIgnoreErrorsSync(item: SessionDuration)

    @Query("SELECT * FROM session_duration LIMIT :pageSize OFFSET :offset")
    fun getSessionDurationPageSync(offset: Int, pageSize: Int): List<SessionDuration>

    @Query("SELECT * FROM session_duration WHERE category_id = :categoryId AND max_session_duration = :maxSessionDuration AND session_pause_duration = :sessionPauseDuration AND start_minute_of_day = :startMinuteOfDay AND end_minute_of_day = :endMinuteOfDay")
    fun getSessionDurationItemSync(
            categoryId: String, maxSessionDuration: Int, sessionPauseDuration: Int, startMinuteOfDay: Int, endMinuteOfDay: Int
    ): SessionDuration?

    @Query("SELECT * FROM session_duration WHERE category_id = :categoryId")
    fun getSessionDurationItemsByCategoryIdSync(categoryId: String): List<SessionDuration>

    @Query("SELECT * FROM session_duration WHERE category_id = :categoryId AND start_minute_of_day >= :startMinuteOfDay AND end_minute_of_day <= :endMinuteOfDay AND max_session_duration >= :maxSessionDuration AND session_pause_duration <= :sessionPauseDuration")
    fun getFittingSessionDurationItemsSync(categoryId: String, startMinuteOfDay: Int, endMinuteOfDay: Int, maxSessionDuration: Int, sessionPauseDuration: Int): List<SessionDuration>

    @Insert
    fun insertSessionDurationItemSync(item: SessionDuration)

    @Insert
    fun insertSessionDurationItemsSync(item: List<SessionDuration>)

    @Update
    fun updateSessionDurationItemSync(item: SessionDuration)

    @Query("DELETE FROM session_duration WHERE last_usage + MIN(session_pause_duration + 1000 * 60 * 60, 1000 * 60 * 60 * 24) < :trustedTimestamp")
    fun deleteOldSessionDurationItemsSync(trustedTimestamp: Long)

    @Query("DELETE FROM session_duration WHERE category_id = :categoryId")
    fun deleteByCategoryId(categoryId: String)
}