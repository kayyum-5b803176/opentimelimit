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
package io.timelimit.android.u2f.usb.descriptors

import io.timelimit.android.u2f.usb.UsbException

data class ReportDescriptor (val items: List<Item>) {
    companion object {
        fun parse(input: ByteArray) = ReportDescriptor(Item.parseList(input))

        data class Item(
            val tag: Int,
            val type: Int,
            val data: ByteArray
        ) {
            companion object {
                object Type {
                    const val MAIN = 0
                    const val GLOBAL = 1
                    const val LOCAL = 2
                }

                val usagePageFido = Item(tag = 0, type = Type.GLOBAL, data = byteArrayOf(0xd0.toByte(), 0xf1.toByte()))
                val usageU2F = Item(tag = 0, type = Type.LOCAL, data = byteArrayOf(1))

                fun parse(input: ByteArray): Pair<Item, ByteArray> {
                    if (input.isEmpty())
                        throw UsbException.InvalidDescriptorLengthException()

                    val baseTag = input[0].toUByte().toInt().ushr(4)
                    val tag: Int
                    val type = input[0].toUByte().toInt().ushr(2) and 3
                    val dataLength: Int
                    val dataOffset: Int

                    if (baseTag == 15) {
                        // long item

                        if (input.size < 3)
                            throw UsbException.InvalidDescriptorLengthException()

                        tag = input[2].toUByte().toInt()
                        dataLength = input[1].toUByte().toInt()
                        dataOffset = 3
                    } else {
                        // regular item
                        tag = baseTag
                        dataLength = when (input[0].toUByte().toInt() and 3) {
                            0 -> 0
                            1 -> 1
                            2 -> 2
                            3 -> 4
                            else -> throw IllegalStateException()
                        }
                        dataOffset = 1
                    }

                    if (input.size < dataOffset + dataLength)
                        throw UsbException.InvalidDescriptorLengthException()

                    val data = input.sliceArray(dataOffset until dataOffset + dataLength)

                    return Item(
                        tag = tag,
                        type = type,
                        data = data
                    ) to input.sliceArray(dataOffset + dataLength until input.size)
                }

                fun parseList(input: ByteArray): List<Item> {
                    val result = mutableListOf<Item>()

                    var remaining = input

                    while (!remaining.isEmpty()) {
                        val (newItem, newRemaining) = parse(remaining)

                        remaining = newRemaining
                        result.add(newItem)
                    }

                    return result
                }
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Item

                if (tag != other.tag) return false
                if (type != other.type) return false
                if (!data.contentEquals(other.data)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = tag
                result = 31 * result + type
                result = 31 * result + data.contentHashCode()
                return result
            }
        }
    }

    val isU2F = kotlin.run {
        val index1 = items.indexOf(Item.usagePageFido)
        val index2 = items.indexOf(Item.usageU2F)

        -1 < index1 && index1 < index2
    }
}