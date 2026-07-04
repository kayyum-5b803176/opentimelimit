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

data class InterfaceDescriptor(
    val index: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val hid: HidDescriptor?,
    val endpoints: List<EndpointDescriptor>
) {
    companion object {
        const val DESCRIPTOR_TYPE = 4.toByte()

        fun parse(input: ByteArray): Pair<InterfaceDescriptor, ByteArray> {
            val descriptorLength = input[0].toUByte().toInt()

            if (descriptorLength < 9 || input.size < descriptorLength)
                throw UsbException.InvalidDescriptorLengthException()

            if (input[1] != DESCRIPTOR_TYPE)
                throw UsbException.InvalidDescriptorTypeException()

            val index = input[2].toUByte().toInt()
            val numEndpoints = input[4].toUByte().toInt()
            val interfaceClass = input[5].toUByte().toInt()
            val interfaceSubclass = input[6].toUByte().toInt()
            val interfaceProtocol = input[7].toUByte().toInt()

            var hid: HidDescriptor? = null
            val endpoints = mutableListOf<EndpointDescriptor>()

            var remaining = input.sliceArray(descriptorLength until input.size)

            while (remaining.isNotEmpty()) {
                if (remaining.size < 2)
                    throw UsbException.InvalidDescriptorLengthException()

                val type = remaining[1]

                if (type == DESCRIPTOR_TYPE) break
                else if (type == EndpointDescriptor.DESCRIPTOR_TYPE) {
                    val (newDescriptor, newRemaining) = EndpointDescriptor.parse(remaining)

                    remaining = newRemaining
                    endpoints.add(newDescriptor)
                } else if (type == HidDescriptor.DESCRIPTOR_TYPE) {
                    val (newDescriptor, newRemaining) = HidDescriptor.parse(remaining)

                    remaining = newRemaining
                    hid = newDescriptor
                } else remaining = UnknownDescriptor.parse(remaining)
            }

            if (numEndpoints != endpoints.size)
                throw UsbException.WrongCounterException("endpoints", numEndpoints, endpoints.size)

            return InterfaceDescriptor(
                index = index,
                interfaceClass = interfaceClass,
                interfaceSubclass = interfaceSubclass,
                interfaceProtocol = interfaceProtocol,
                hid = hid,
                endpoints = endpoints
            ) to remaining
        }
    }
}