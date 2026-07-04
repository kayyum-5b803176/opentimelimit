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

package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(
        tableName = "child_task",
        foreignKeys = [
            ForeignKey(
                    entity = Category::class,
                    childColumns = ["category_id"],
                    parentColumns = ["id"],
                    onUpdate = ForeignKey.CASCADE,
                    onDelete = ForeignKey.CASCADE
            )
        ]
)
data class ChildTask(
        @PrimaryKey
        @ColumnInfo(name = "task_id")
        val taskId: String,
        @ColumnInfo(name = "category_id", index = true)
        val categoryId: String,
        @ColumnInfo(name = "task_title")
        val taskTitle: String,
        @ColumnInfo(name = "extra_time_duration")
        val extraTimeDuration: Int,
        @ColumnInfo(name = "pending_request")
        val pendingRequest: Boolean,
        // 0 = not yet granted
        @ColumnInfo(name = "last_grant_timestamp")
        val lastGrantTimestamp: Long
): JsonSerializable {
    companion object {
        private const val TASK_ID = "taskId"
        private const val CATEGORY_ID = "categoryId"
        private const val TASK_TITLE = "taskTitle"
        private const val EXTRA_TIME_DURATION = "extraTimeDuration"
        private const val PENDING_REQUEST = "pendingRequest"
        private const val LAST_GRANT_TIMESTAMP = "lastGrantTimestamp"

        const val MAX_EXTRA_TIME = 1000 * 60 * 60 * 24
        const val MAX_TASK_TITLE_LENGTH = 50

        fun parse(reader: JsonReader): ChildTask {
            var taskId: String? = null
            var categoryId: String? = null
            var taskTitle: String? = null
            var extraTimeDuration: Int? = null
            var pendingRequest: Boolean? = null
            var lastGrantTimestamp: Long? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    TASK_ID -> taskId = reader.nextString()
                    CATEGORY_ID -> categoryId = reader.nextString()
                    TASK_TITLE -> taskTitle = reader.nextString()
                    EXTRA_TIME_DURATION -> extraTimeDuration = reader.nextInt()
                    PENDING_REQUEST -> pendingRequest = reader.nextBoolean()
                    LAST_GRANT_TIMESTAMP -> lastGrantTimestamp = reader.nextLong()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return ChildTask(
                    taskId = taskId!!,
                    categoryId = categoryId!!,
                    taskTitle = taskTitle!!,
                    extraTimeDuration = extraTimeDuration!!,
                    pendingRequest = pendingRequest!!,
                    lastGrantTimestamp = lastGrantTimestamp!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(taskId)
        IdGenerator.assertIdValid(categoryId)

        if (taskTitle.isEmpty() || taskTitle.length > MAX_TASK_TITLE_LENGTH) throw IllegalArgumentException()
        if (extraTimeDuration <= 0 || extraTimeDuration > MAX_EXTRA_TIME) throw IllegalArgumentException()
        if (lastGrantTimestamp < 0) throw IllegalArgumentException()
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(TASK_ID).value(taskId)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(TASK_TITLE).value(taskTitle)
        writer.name(EXTRA_TIME_DURATION).value(extraTimeDuration)
        writer.name(PENDING_REQUEST).value(pendingRequest)
        writer.name(LAST_GRANT_TIMESTAMP).value(lastGrantTimestamp)

        writer.endObject()
    }
}