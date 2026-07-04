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

sealed class U2FRequest {
    abstract val ins: Byte
    abstract val p1: Byte
    abstract val p2: Byte
    abstract val payload: ByteArray

    fun encodeShort(): ByteArray {
        val cla: Byte = 0

        if (payload.size > 255) {
            throw U2FException.CommunicationException()
        }

        return byteArrayOf(
            cla,
            ins,
            p1,
            p2,
            payload.size.toByte()
        ) + payload + byteArrayOf(0)
    }

    fun encodeExtended(): ByteArray {
        val cla: Byte = 0

        if (payload.size > 65535) {
            throw U2FException.CommunicationException()
        }

        return byteArrayOf(
            cla,
            ins,
            p1,
            p2,
            0,
            payload.size.ushr(8).toByte(),
            payload.size.toByte()
        ) + payload + byteArrayOf(1, 0)
    }
    data class Register(
        val challenge: ByteArray,
        val applicationId: ByteArray
    ): U2FRequest() {
        override val ins: Byte = 1
        override val p1: Byte = 0
        override val p2: Byte = 0
        override val payload: ByteArray = challenge + applicationId

        init {
            if (challenge.size != 32 || applicationId.size != 32) {
                throw IllegalArgumentException()
            }
        }
    }

    data class Login(
        val mode: Mode,
        val challenge: ByteArray,
        val applicationId: ByteArray,
        val keyHandle: ByteArray
    ): U2FRequest() {
        override val ins: Byte = 2
        override val p1: Byte = when (mode) {
            Mode.EnforcePresence -> 3
            Mode.CheckOnly -> 7
            Mode.DoNotEnforcePresence -> 8
        }
        override val p2: Byte = 0
        override val payload: ByteArray = challenge + applicationId + keyHandle.size.toByte() + keyHandle

        init {
            if (challenge.size != 32 || applicationId.size != 32 || keyHandle.size > 255) {
                throw IllegalArgumentException()
            }
        }

        enum class Mode {
            CheckOnly,
            EnforcePresence,
            DoNotEnforcePresence
        }
    }
}
