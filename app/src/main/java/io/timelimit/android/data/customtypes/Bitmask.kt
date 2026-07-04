/*
 * Open TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.data.customtypes

import android.os.Parcelable
import android.text.TextUtils
import androidx.room.TypeConverter
import kotlinx.parcelize.Parcelize
import java.util.*
import kotlin.collections.ArrayList

sealed class Bitmask(private val data: BitSet) {
    fun read(index: Int): Boolean {
        return data[index]
    }
}

data class MutableBitmask (val data: BitSet): Bitmask(data) {
    fun write(index: Int, value: Boolean) {
        data[index] = value
    }

    fun toImmutable(): ImmutableBitmask {
        return ImmutableBitmask(data.clone() as BitSet)
    }
}

@Parcelize
data class ImmutableBitmask(val dataNotToModify: BitSet): Bitmask(dataNotToModify), Parcelable {
    fun toMutable(): MutableBitmask {
        return MutableBitmask(dataNotToModify)
    }
}

// format: index of start of set bits, index of stop of set bits, ...
class ImmutableBitmaskAdapter {
    @TypeConverter
    fun toString(mask: ImmutableBitmask) = ImmutableBitmaskJson.serialize(mask)

    @TypeConverter
    fun toImmutableBitmask(data: String) = ImmutableBitmaskJson.parse(data, null)
}

object ImmutableBitmaskJson {
    fun serialize(mask: ImmutableBitmask): String {
        val output = ArrayList<Int>()

        if (mask.read(0)) {
            // if first bit is set

            output.add(0)
        } else {
            // if first bit is not set

            val start = mask.dataNotToModify.nextSetBit(0)

            if (start == -1) {
                // nothing is set
                return ""
            }

            output.add(start)
        }

        do {
            val startIndex = output.last()
            val stopIndex = mask.dataNotToModify.nextClearBit(startIndex)

            output.add(stopIndex)

            val nextStartIndex = mask.dataNotToModify.nextSetBit(stopIndex)
            if (nextStartIndex == -1) {
                break
            } else {
                output.add(nextStartIndex)
            }
        } while (true)

        return TextUtils.join(",", output.map { it.toString() })
    }

    fun parse(data: String, maxSize: Int?): ImmutableBitmask {
        val indexes = data.split(",").filter{ it.isNotBlank() }.map { it.toInt() }
        val iterator = indexes.iterator()
        val output = BitSet()

        while (iterator.hasNext()) {
            val start = iterator.next()
            val end = iterator.next()

            if (maxSize != null) {
                if (start > maxSize || end > maxSize) {
                    throw IllegalArgumentException()
                }
            }

            output.set(start, end)
        }

        return ImmutableBitmask(output)
    }
}
