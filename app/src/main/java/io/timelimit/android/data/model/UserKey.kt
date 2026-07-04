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

import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.*
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(
        tableName = "user_key",
        foreignKeys = [
            ForeignKey(
                    entity = User::class,
                    parentColumns = ["id"],
                    childColumns = ["user_id"],
                    onUpdate = ForeignKey.CASCADE,
                    onDelete = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    value = ["key"],
                    unique = true
            )
        ]
)
data class UserKey(
        @PrimaryKey
        @ColumnInfo(name = "user_id")
        val userId: String,
        @ColumnInfo(name = "key")
        val publicKey: ByteArray,
        @ColumnInfo(name = "last_use")
        val lastUse: Long
): JsonSerializable {
    companion object {
        private const val USER_ID = "userId"
        private const val PUBLIC_KEY = "publicKey"
        private const val LAST_USE = "lastUse"

        fun parse(reader: JsonReader): UserKey {
            var userId: String? = null
            var publicKey: ByteArray? = null
            var lastUse: Long? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    USER_ID -> userId = reader.nextString()
                    PUBLIC_KEY -> publicKey = Base64.decode(reader.nextString(), 0)
                    LAST_USE -> lastUse = reader.nextLong()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return UserKey(
                    userId = userId!!,
                    publicKey = publicKey!!,
                    lastUse = lastUse!!
            )
        }
    }

    init {
        if (publicKey.size != Curve25519.PUBLIC_KEY_SIZE) {
            throw IllegalArgumentException()
        }

        IdGenerator.assertIdValid(userId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(USER_ID).value(userId)
        writer.name(PUBLIC_KEY).value(Base64.encodeToString(publicKey, Base64.NO_WRAP))
        writer.name(LAST_USE).value(lastUse)

        writer.endObject()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserKey

        if (userId != other.userId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (lastUse != other.lastUse) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + lastUse.hashCode()
        return result
    }
}