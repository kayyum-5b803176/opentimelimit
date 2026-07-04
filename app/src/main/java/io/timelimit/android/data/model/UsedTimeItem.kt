/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.extensions.MinuteOfDay

@Entity(primaryKeys = ["category_id", "day_of_epoch", "start_time_of_day", "end_time_of_day"], tableName = "used_time")
data class UsedTimeItem(
        @ColumnInfo(name = "day_of_epoch")
        val dayOfEpoch: Int,
        @ColumnInfo(name = "used_time")
        val usedMillis: Long,
        @ColumnInfo(name = "category_id")
        val categoryId: String,
        @ColumnInfo(name = "start_time_of_day")
        val startTimeOfDay: Int,
        @ColumnInfo(name = "end_time_of_day")
        val endTimeOfDay: Int
): JsonSerializable {
    companion object {
        private const val DAY_OF_EPOCH = "day"
        private const val USED_TIME_MILLIS = "time"
        private const val CATEGORY_ID = "category"
        private const val START_TIME_OF_DAY = "start"
        private const val END_TIME_OF_DAY = "end"

        fun parse(reader: JsonReader): UsedTimeItem {
            reader.beginObject()

            var dayOfEpoch: Int? = null
            var usedMillis: Long? = null
            var categoryId: String? = null
            var startTimeOfDay = MinuteOfDay.MIN
            var endTimeOfDay = MinuteOfDay.MAX

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DAY_OF_EPOCH -> dayOfEpoch = reader.nextInt()
                    USED_TIME_MILLIS -> usedMillis = reader.nextLong()
                    CATEGORY_ID -> categoryId = reader.nextString()
                    START_TIME_OF_DAY -> startTimeOfDay = reader.nextInt()
                    END_TIME_OF_DAY -> endTimeOfDay = reader.nextInt()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return UsedTimeItem(
                    dayOfEpoch = dayOfEpoch!!,
                    usedMillis = usedMillis!!,
                    categoryId = categoryId!!,
                    startTimeOfDay = startTimeOfDay,
                    endTimeOfDay = endTimeOfDay
            )
        }
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (dayOfEpoch < 0) {
            throw IllegalArgumentException()
        }

        if (usedMillis < 0) {
            throw IllegalArgumentException()
        }

        if (startTimeOfDay < MinuteOfDay.MIN || endTimeOfDay > MinuteOfDay.MAX || startTimeOfDay > endTimeOfDay) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(DAY_OF_EPOCH).value(dayOfEpoch)
        writer.name(USED_TIME_MILLIS).value(usedMillis)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(START_TIME_OF_DAY).value(startTimeOfDay)
        writer.name(END_TIME_OF_DAY).value(endTimeOfDay)

        writer.endObject()
    }
}
