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
package io.timelimit.android.u2f.nfc

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.u2f.protocol.U2FDeviceSession
import io.timelimit.android.u2f.util.U2FException
import io.timelimit.android.u2f.util.U2FThread
import io.timelimit.android.u2f.protocol.U2FDevice
import java.io.IOException

class NfcU2FDevice(private val tag: IsoDep): U2FDevice {
    override suspend fun connect(): U2FDeviceSession {
        return U2FThread.nfc.executeAndWait {
            try {
                tag.connect()

                // https://fidoalliance.org/specs/fido-u2f-v1.2-ps-20170411/fido-u2f-nfc-protocol-v1.2-ps-20170411.html#applet-selection
                tag.transceive(
                    byteArrayOf(
                        0x00,
                        0xA4.toByte(),
                        0x04,
                        0x00,
                        0x08,
                        0xA0.toByte(),
                        0x00,
                        0x00,
                        0x06,
                        0x47,
                        0x2F,
                        0x00,
                        0x01
                    )
                )

                NfcU2FDeviceSession(tag)
            } catch (ex: TagLostException) {
                throw U2FException.DisconnectedException()
            } catch (ex: IOException) {
                throw U2FException.CommunicationException()
            }
        }
    }
}