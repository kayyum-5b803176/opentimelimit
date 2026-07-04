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
import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.u2f.protocol.U2FDeviceSession
import io.timelimit.android.u2f.protocol.U2FRequest
import io.timelimit.android.u2f.protocol.U2fRawResponse
import io.timelimit.android.u2f.util.U2FException
import io.timelimit.android.u2f.util.U2FThread
import java.io.IOException

class NfcU2FDeviceSession(private val tag: IsoDep): U2FDeviceSession {
    companion object {
        private const val LOG_TAG = "NfcU2FDeviceSession"
    }

    override suspend fun execute(request: U2FRequest): U2fRawResponse = U2FThread.nfc.executeAndWait {
        try {
            var response = U2fRawResponse.decode(tag.transceive(request.encodeShort()))
            var fullPayload = response.payload

            // https://fidoalliance.org/specs/fido-u2f-v1.2-ps-20170411/fido-u2f-nfc-protocol-v1.2-ps-20170411.html
            // the response could be split into multiple parts
            while (response.status.toInt().ushr(8) == 0x61) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got partial response: $response")
                }

                response = U2fRawResponse.decode(
                    tag.transceive(
                        byteArrayOf(
                            0, 0xc0.toByte(), 0, 0, response.status.toByte()
                        )
                    )
                )

                fullPayload += response.payload

                if (fullPayload.size > 65535) throw U2FException.CommunicationException()
            }

            response = response.copy(payload = fullPayload)

            response.throwIfNoSuccess()

            response
        } catch (ex: TagLostException) {
            throw U2FException.DisconnectedException()
        } catch (ex: IOException) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "got no response", ex)
            }

            throw U2FException.CommunicationException()
        }
    }

    override fun close() {
        U2FThread.nfc.submit {
            try {
                tag.close()
            } catch (ex: IOException) {
                // ignore
            }
        }
    }
}