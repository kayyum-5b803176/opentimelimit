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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.timelimit.android.data.JsonSerializable

@Entity(tableName = "allowed_contact")
data class AllowedContact(
        @PrimaryKey(autoGenerate = true)
        val id: Int,
        val title: String,
        val phone: String
): JsonSerializable {
    companion object {
        private const val ID = "id"
        private const val TITLE = "title"
        private const val PHONE = "phone"

        fun parse(reader: JsonReader): AllowedContact {
            var id: Int? = null
            var title: String? = null
            var phone: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextInt()
                    TITLE -> title = reader.nextString()
                    PHONE -> phone = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AllowedContact(
                    id = id!!,
                    title = title!!,
                    phone = phone!!
            )
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(TITLE).value(title)
        writer.name(PHONE).value(phone)

        writer.endObject()
    }
}
