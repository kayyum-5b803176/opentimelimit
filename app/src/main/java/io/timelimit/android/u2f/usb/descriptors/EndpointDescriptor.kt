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

data class EndpointDescriptor(val address: Int, val attributes: Int, val maxPacketSize: Int) {
    companion object {
        const val DESCRIPTOR_TYPE = 5.toByte()

        fun parse(input: ByteArray): Pair<EndpointDescriptor, ByteArray> {
            val descriptorLength = input[0].toUByte().toInt()

            if (descriptorLength < 7 || input.size < descriptorLength)
                throw UsbException.InvalidDescriptorLengthException()

            if (input[1] != DESCRIPTOR_TYPE)
                throw UsbException.InvalidDescriptorTypeException()

            val address = input[2].toUByte().toInt()
            val attributes = input[3].toUByte().toInt()
            val maxPacketSize = input[4].toUByte().toInt() or input[5].toUByte().toInt().shl(8)

            return EndpointDescriptor(
                address = address,
                attributes = attributes,
                maxPacketSize = maxPacketSize
            ) to input.sliceArray(descriptorLength until input.size)
        }
    }
}