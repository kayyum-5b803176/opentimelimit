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
package io.timelimit.android.u2f.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.extensions.some
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.u2f.protocol.U2FDeviceSession
import io.timelimit.android.u2f.usb.descriptors.DeviceDescriptor
import io.timelimit.android.u2f.usb.descriptors.ReportDescriptor
import io.timelimit.android.u2f.util.U2FException
import io.timelimit.android.u2f.util.U2FThread

data class UsbU2FDevice (
    val device: UsbDevice,
    val permissionRequestManager: UsbPermissionRequestManager,
    val usbManager: UsbManager,
    val disconnectReporter: DisconnectReporter
): U2FDevice {
    companion object {
        private const val LOG_TAG = "UsbU2FDevice"
        private const val CLASS_HID = 3
        private const val ATTRIBUTES_INTERRUPT = 3
        private const val ATTRIBUTES_INTERRUPT_MASK = 3

        // https://fidoalliance.org/specs/fido-u2f-v1.0-ps-20141009/fido-u2f-hid-protocol-ps-20141009.html
        // http://esd.cs.ucr.edu/webres/usb11.pdf
        // https://www.usb.org/sites/default/files/documents/hid1_11.pdf
        fun from(
            device: UsbDevice,
            permissionRequestManager: UsbPermissionRequestManager,
            usbManager: UsbManager,
            disconnectReporter: DisconnectReporter
        ): UsbU2FDevice? {
            for (usbInterface in device.interfaces) {
                // according to the U2F spec, there must be a input and output endpoint
                // according to the HID spec, the endpoints must be data interrupt endpoints
                // it looks like the U2F spec wants no subclass and protocol that is not zero
                // it looks like the HID spec does not allow more than one endpoint per type
                if (
                    usbInterface.interfaceClass != CLASS_HID ||
                    usbInterface.interfaceSubclass != 0 ||
                    usbInterface.interfaceProtocol != 0 ||
                    usbInterface.endpointCount != 2
                ) continue

                val hasInputEndpoint = usbInterface.endpoints.some {
                    it.attributes and ATTRIBUTES_INTERRUPT_MASK == ATTRIBUTES_INTERRUPT &&
                    it.direction == UsbConstants.USB_DIR_IN
                }

                val hasOutputEndpoint = usbInterface.endpoints.some {
                    it.attributes and ATTRIBUTES_INTERRUPT_MASK == ATTRIBUTES_INTERRUPT &&
                    it.direction == UsbConstants.USB_DIR_OUT
                }

                if (hasInputEndpoint && hasOutputEndpoint) {
                    return UsbU2FDevice(
                        device = device,
                        permissionRequestManager = permissionRequestManager,
                        usbManager = usbManager,
                        disconnectReporter = disconnectReporter
                    )
                }
            }

            return null
        }
    }

    override suspend fun connect(): U2FDeviceSession {
        if (!permissionRequestManager.requestPermission(device)) {
            throw U2FException.CommunicationException()
        }

        val connection = usbManager.openDevice(device)

        try {
            val descriptors = DeviceDescriptor.parse(connection.rawDescriptors)

            for (configuration in descriptors.configurations) {
                for (usbInterface in configuration.interfaces) {
                    // same as above
                    if (
                        usbInterface.interfaceClass != CLASS_HID ||
                        usbInterface.interfaceSubclass != 0 ||
                        usbInterface.interfaceProtocol != 0 ||
                        usbInterface.endpoints.size != 2 ||
                        usbInterface.hid == null
                    ) continue

                    val inputEndpoint = usbInterface.endpoints.find {
                        it.attributes and ATTRIBUTES_INTERRUPT_MASK == ATTRIBUTES_INTERRUPT &&
                                it.address and UsbConstants.USB_ENDPOINT_DIR_MASK == UsbConstants.USB_DIR_IN
                    }

                    val outputEndpoint = usbInterface.endpoints.find {
                        it.attributes and ATTRIBUTES_INTERRUPT_MASK == ATTRIBUTES_INTERRUPT &&
                                it.address and UsbConstants.USB_ENDPOINT_DIR_MASK == UsbConstants.USB_DIR_OUT
                    }

                    if (inputEndpoint == null || outputEndpoint == null) continue

                    val inputEndpointIndex = usbInterface.endpoints.indexOf(inputEndpoint)
                    val outputEndpointIndex = usbInterface.endpoints.indexOf(outputEndpoint)

                    val hidDescriptorRaw = U2FThread.usb.executeAndWait {
                        val buffer = ByteArray(usbInterface.hid.reportDescriptorSize)

                        if (!connection.claimInterface(
                                device.getInterface(usbInterface.index),
                                true
                            )
                        ) {
                            throw U2FException.CommunicationException()
                        }

                        val size = connection.controlTransfer(
                            1 /* interface */ or 0x80 /* data from device to host */,
                            6, /* GET_DESCRIPTOR */
                            0x2200, /* first report descriptor (there is always a single one) */
                            usbInterface.index, /* interface index */
                            buffer, /* data */
                            buffer.size, /* length */
                            100 /* timeout */
                        )

                        if (size != buffer.size)
                            throw U2FException.CommunicationException()

                        buffer
                    }

                    val hidDescriptor = ReportDescriptor.parse(hidDescriptorRaw)

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            LOG_TAG,
                            "found device: $hidDescriptor; isU2F = ${hidDescriptor.isU2F}"
                        )
                    }

                    if (!hidDescriptor.isU2F) continue

                    val u2FConnection = UsbU2FDeviceConnection(
                        inputEndpoint = device.getInterface(usbInterface.index)
                            .getEndpoint(inputEndpointIndex),
                        outputEndpoint = device.getInterface(usbInterface.index)
                            .getEndpoint(outputEndpointIndex),
                        connection = connection,
                        disconnectReporter = disconnectReporter
                    )

                    try {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "created connection")
                        }

                        u2FConnection.ping(16)
                        u2FConnection.ping(128)

                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "pings worked")
                        }

                        return u2FConnection
                    } catch (ex: Exception) {
                        u2FConnection.cancelPendingRequests()

                        throw ex
                    }
                }

                break // does not support multiple configurations yet
            }

            throw U2FException.CommunicationException()
        } catch (ex: UsbException) {
            throw U2FException.CommunicationException()
        } catch (ex: Exception) {
            connection.close()

            throw ex
        }
    }
}