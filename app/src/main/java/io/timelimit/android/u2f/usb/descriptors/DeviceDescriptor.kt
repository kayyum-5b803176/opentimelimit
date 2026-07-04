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

data class DeviceDescriptor(val configurations: List<ConfigurationDescriptor>) {
    companion object {
        private const val DESCRIPTOR_TYPE = 1.toByte()

        fun parse(input: ByteArray): DeviceDescriptor {
            val descriptorLength = input[0].toUByte().toInt()

            if (descriptorLength < 17 || input.size < descriptorLength)
                throw UsbException.InvalidDescriptorLengthException()

            if (input[1] != DESCRIPTOR_TYPE)
                throw UsbException.InvalidDescriptorTypeException()

            val numConfigurations = input[17].toUByte().toInt()
            val configurations = mutableListOf<ConfigurationDescriptor>()

            var remaining = input.sliceArray(descriptorLength until input.size)

            while (remaining.isNotEmpty()) {
                if (remaining.size < 2)
                    throw UsbException.InvalidDescriptorLengthException()

                if (remaining[1] == ConfigurationDescriptor.DESCRIPTOR_TYPE) {
                    val (newDescriptor, newRemaining) = ConfigurationDescriptor.parse(remaining)

                    remaining = newRemaining
                    configurations.add(newDescriptor)
                } else remaining = UnknownDescriptor.parse(remaining)
            }

            if (numConfigurations != configurations.size)
                throw UsbException.WrongCounterException("configuration", numConfigurations, configurations.size)

            return DeviceDescriptor(configurations = configurations)
        }
    }
}