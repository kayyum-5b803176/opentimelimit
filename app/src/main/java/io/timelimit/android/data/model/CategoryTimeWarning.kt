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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.*
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(
    tableName = "category_time_warning",
    primaryKeys = ["category_id", "minutes"],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CategoryTimeWarning (
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    val minutes: Int
): JsonSerializable {
    companion object {
        private const val CATEGORY_ID = "categoryId"
        private const val MINUTES = "minutes"
        const val MIN = 1
        const val MAX = 60 * 24 * 7 - 2

        fun parse(reader: JsonReader): CategoryTimeWarning {
            var categoryId: String? = null
            var minutes: Int? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    MINUTES -> minutes = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return CategoryTimeWarning(
                categoryId = categoryId!!,
                minutes = minutes!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(categoryId)

        if (minutes < MIN || minutes > MAX) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(MINUTES).value(minutes)

        writer.endObject()
    }
}