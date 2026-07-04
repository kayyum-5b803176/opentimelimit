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

data class U2fRawResponse (
    val status: UShort,
    val payload: ByteArray
) {
    companion object {
        fun decode(data: ByteArray): U2fRawResponse {
            if (data.size < 2) throw U2FException.CommunicationException()

            return U2fRawResponse(
                payload = data.sliceArray(0..(data.size - 3)),
                status = data[data.size - 1].toUByte().toUShort() or (data[data.size - 2].toUByte().toUInt().shl(8).toUShort())
            )
        }
    }

    fun throwIfNoSuccess() {
        if (status == 0x6A80.toUShort()) throw U2FException.BadKeyHandleException()
        if (status == 0x6985.toUShort()) throw U2FException.UserInteractionRequired()
        if (status == 0x6700.toUShort()) throw U2FException.BadRequestLength()
        if (status != 0x9000.toUShort()) throw U2FException.DeviceException(status)
    }
}