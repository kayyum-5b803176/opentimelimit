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
import io.timelimit.android.crypto.HexString
import io.timelimit.android.crypto.Sha512
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(
        tableName = "category_network_id",
        primaryKeys = ["category_id", "network_item_id"],
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
data class CategoryNetworkId(
        @ColumnInfo(name = "category_id")
        val categoryId: String,
        @ColumnInfo(name = "network_item_id")
        val networkItemId: String,
        @ColumnInfo(name = "hashed_network_id")
        val hashedNetworkId: String
): JsonSerializable {
    companion object {
        private const val CATEGORY_ID = "categoryId"
        private const val NETWORK_ITEM_ID = "networkItemId"
        private const val HASHED_NETWORK_ID = "hashedNetworkId"
        const val ANONYMIZED_NETWORK_ID_LENGTH = 8
        const val MAX_ITEMS = 8

        fun anonymizeNetworkId(itemId: String, networkId: String) = Sha512.hashSync(itemId + networkId).substring(0, ANONYMIZED_NETWORK_ID_LENGTH)

        fun parse(reader: JsonReader): CategoryNetworkId {
            var categoryId: String? = null
            var networkItemId: String? = null
            var hashedNetworkId: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    CATEGORY_ID -> categoryId = reader.nextString()
                    NETWORK_ITEM_ID -> networkItemId = reader.nextString()
                    HASHED_NETWORK_ID -> hashedNetworkId = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return CategoryNetworkId(
                    categoryId = categoryId!!,
                    networkItemId = networkItemId!!,
                    hashedNetworkId = hashedNetworkId!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(categoryId)
        IdGenerator.assertIdValid(networkItemId)
        if (hashedNetworkId.length != ANONYMIZED_NETWORK_ID_LENGTH) throw IllegalArgumentException()
        HexString.assertIsHexString(hashedNetworkId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(NETWORK_ITEM_ID).value(networkItemId)
        writer.name(HASHED_NETWORK_ID).value(hashedNetworkId)

        writer.endObject()
    }
}