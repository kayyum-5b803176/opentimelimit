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

data class HidDescriptor(val reportDescriptorSize: Int) {
    companion object {
        const val DESCRIPTOR_TYPE = 33.toByte()

        fun parse(input: ByteArray): Pair<HidDescriptor, ByteArray> {
            val descriptorLength = input[0].toUByte().toInt()

            if (descriptorLength < 9 || input.size < descriptorLength)
                throw UsbException.InvalidDescriptorLengthException()

            if (input[1] != DESCRIPTOR_TYPE)
                throw UsbException.InvalidDescriptorTypeException()

            val reportDescriptorSize = input[7].toUByte().toInt() or input[8].toUByte().toInt().shl(8)

            return HidDescriptor(
                reportDescriptorSize = reportDescriptorSize
            ) to input.sliceArray(descriptorLength until input.size)
        }
    }
}