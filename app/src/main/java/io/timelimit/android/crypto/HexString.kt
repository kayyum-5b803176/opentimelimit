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
package io.timelimit.android.crypto

object HexString {
    fun toHex(data: ByteArray): String {
        return StringBuilder(data.size * 2).apply {
            for (i in data.indices) {
                append(Integer.toString(data[i].toInt() shr 4 and 15, 16))
                append(Integer.toString(data[i].toInt() and 15, 16))
            }
        }.toString()
    }

    fun fromHex(value: String): ByteArray {
        if (value.length % 2 != 0) {
            throw IllegalArgumentException()
        }

        val result = ByteArray(value.length / 2)

        for (index in result.indices) {
            result.set(
                    index = index,
                    value = (
                            (Integer.parseInt(value[index * 2 + 0].toString(), 16) shl 4) or
                                    Integer.parseInt(value[index * 2 + 1].toString(), 16)
                            ).toByte()
            )
        }

        return result
    }

    fun assertIsHexString(value: String) {
        if (value.length % 2 != 0) {
            throw IllegalArgumentException()
        }

        value.forEach {
            char ->

            if ("0123456789abcdef".indexOf(char) == -1) {
                throw IllegalArgumentException()
            }
        }
    }
}
