/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.extensions.MinuteOfDay

@Entity(
        tableName = "session_duration",
        primaryKeys = [
            "category_id",
            "max_session_duration",
            "session_pause_duration",
            "start_minute_of_day",
            "end_minute_of_day"
        ],
        foreignKeys = [
            ForeignKey(
                    entity = Category::class,
                    parentColumns = ["id"],
                    childColumns = ["category_id"],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    name = "session_duration_index_category_id",
                    value = ["category_id"],
                    unique = false
            )
        ]
)
data class SessionDuration(
        @ColumnInfo(name = "category_id")
        val categoryId: String,
        @ColumnInfo(name = "max_session_duration")
        val maxSessionDuration: Int,
        @ColumnInfo(name = "session_pause_duration")
        val sessionPauseDuration: Int,
        @ColumnInfo(name = "start_minute_of_day")
        val startMinuteOfDay: Int,
        @ColumnInfo(name = "end_minute_of_day")
        val endMinuteOfDay: Int,
        @ColumnInfo(name = "last_usage")
        val lastUsage: Long,
        @ColumnInfo(name = "last_session_duration")
        val lastSessionDuration: Long
): JsonSerializable {
    companion object {
        private const val CATEGORY_ID = "c"
        private const val MAX_SESSION_DURATION = "md"
        private const val SESSION_PAUSE_DURATION = "spd"
        private const val START_MINUTE_OF_DAY = "sm"
        private const val END_MINUTE_OF_DAY = "em"
        private const val LAST_USAGE = "l"
        private const val LAST_SESSION_DURATION = "d"

        fun parse(reader: JsonReader): SessionDuration {
            var categoryId: String? = null
            var maxSessionDuration: Int? = null
            var sessionPauseDuration: Int? = null
            var startMinuteOfDay: Int? = null
            var endMinuteOfDay: Int? = null
            var lastUsage: Long? = null
            var lastSessionDuration: Long? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    MAX_SESSION_DURATION -> maxSessionDuration = reader.nextInt()
                    SESSION_PAUSE_DURATION -> sessionPauseDuration = reader.nextInt()
                    START_MINUTE_OF_DAY -> startMinuteOfDay = reader.nextInt()
                    END_MINUTE_OF_DAY -> endMinuteOfDay = reader.nextInt()
                    LAST_USAGE -> lastUsage = reader.nextLong()
                    LAST_SESSION_DURATION -> lastSessionDuration = reader.nextLong()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return SessionDuration(
                    categoryId = categoryId!!,
                    maxSessionDuration = maxSessionDuration!!,
                    sessionPauseDuration = sessionPauseDuration!!,
                    startMinuteOfDay = startMinuteOfDay!!,
                    endMinuteOfDay = endMinuteOfDay!!,
                    lastUsage = lastUsage!!,
                    lastSessionDuration = lastSessionDuration!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (maxSessionDuration <= 0 || sessionPauseDuration <= 0) {
            throw IllegalArgumentException()
        }

        if (!(MinuteOfDay.isValid(startMinuteOfDay) && MinuteOfDay.isValid(endMinuteOfDay))) {
            throw IllegalArgumentException()
        }

        if (lastUsage < 0 || lastSessionDuration < 0) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(MAX_SESSION_DURATION).value(maxSessionDuration)
        writer.name(SESSION_PAUSE_DURATION).value(sessionPauseDuration)
        writer.name(START_MINUTE_OF_DAY).value(startMinuteOfDay)
        writer.name(END_MINUTE_OF_DAY).value(endMinuteOfDay)
        writer.name(LAST_USAGE).value(lastUsage)
        writer.name(LAST_SESSION_DURATION).value(lastSessionDuration)

        writer.endObject()
    }
}