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
package io.timelimit.android.u2f.protocol

import io.timelimit.android.u2f.util.U2FException

object U2FResponse {
    data class Register(
        val publicKey: ByteArray,
        val keyHandle: ByteArray
    ) {
        companion object {
            fun parse(rawResponse: U2fRawResponse): Register {
                if (rawResponse.payload.size < 67) throw U2FException.InvalidDataException()

                val publicKey = rawResponse.payload.sliceArray(1..65)
                val keyHandleLength = rawResponse.payload[66].toUByte().toInt()

                if (rawResponse.payload.size < 67 + keyHandleLength) throw U2FException.InvalidDataException()

                val keyHandle = rawResponse.payload.sliceArray(67 until 67 + keyHandleLength)

                if (publicKey.size != 65 || keyHandle.size != keyHandleLength) throw IllegalStateException()

                return Register(
                    publicKey = publicKey,
                    keyHandle = keyHandle
                )
            }
        }
    }

    data class Login(
        val flags: Byte,
        val counter: UInt,
        val signature: ByteArray
    ) {
        companion object {
            fun parse(rawResponse: U2fRawResponse): Login {
                if (rawResponse.payload.size < 5) throw U2FException.InvalidDataException()

                val flags = rawResponse.payload[0]

                val counter = rawResponse.payload[4].toUInt() or
                        rawResponse.payload[3].toUInt().shl(8) or
                        rawResponse.payload[2].toUInt().shl(16) or
                        rawResponse.payload[1].toUInt().shl(24)

                val signature = rawResponse.payload.sliceArray(5 until rawResponse.payload.size)

                return Login(
                    flags = flags,
                    counter = counter,
                    signature = signature
                )
            }
        }
    }
}