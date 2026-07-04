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

data class ConfigurationDescriptor(val interfaces: List<InterfaceDescriptor>) {
    companion object {
        const val DESCRIPTOR_TYPE = 2.toByte()

        fun parse(input: ByteArray): Pair<ConfigurationDescriptor, ByteArray> {
            val descriptorLength = input[0].toUByte().toInt()

            if (descriptorLength < 9 || input.size < descriptorLength)
                throw UsbException.InvalidDescriptorLengthException()

            if (input[1] != DESCRIPTOR_TYPE)
                throw UsbException.InvalidDescriptorTypeException()

            val totalLength = input[2].toUByte().toInt() or input[3].toUByte().toInt().shl(8)

            if (input.size < totalLength)
                throw UsbException.InvalidDescriptorLengthException()

            val numInterfaces = input[4].toInt()
            val interfaces = mutableListOf<InterfaceDescriptor>()

            var remaining = input.sliceArray(descriptorLength until totalLength)

            while (remaining.isNotEmpty()) {
                if (remaining.size < 2)
                    throw UsbException.InvalidDescriptorLengthException()

                val type = remaining[1]

                if (type == InterfaceDescriptor.DESCRIPTOR_TYPE) {
                    val (newDescriptor, newRemaining) = InterfaceDescriptor.parse(remaining)

                    if (newDescriptor.index != interfaces.size)
                        throw UsbException.InvalidIndexException()

                    remaining = newRemaining
                    interfaces.add(newDescriptor)
                } else remaining = UnknownDescriptor.parse(remaining)
            }

            if (numInterfaces != interfaces.size)
                throw UsbException.WrongCounterException("interfaces", numInterfaces, interfaces.size)

            return ConfigurationDescriptor(
                interfaces = interfaces
            ) to input.sliceArray(totalLength until input.size)
        }
    }
}