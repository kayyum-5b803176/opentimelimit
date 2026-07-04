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

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import android.os.SystemClock
import android.util.Log
import io.timelimit.android.BuildConfig
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.crypto.HexString
import io.timelimit.android.u2f.protocol.U2FDeviceSession
import io.timelimit.android.u2f.protocol.U2FRequest
import io.timelimit.android.u2f.protocol.U2fRawResponse
import io.timelimit.android.u2f.util.U2FException
import io.timelimit.android.u2f.util.U2FThread
import java.nio.ByteBuffer
import java.util.*

class UsbU2FDeviceConnection (
    private val inputEndpoint: UsbEndpoint,
    private val outputEndpoint: UsbEndpoint,
    private val connection: UsbDeviceConnection,
    private val disconnectReporter: DisconnectReporter
): U2FDeviceSession {
    companion object {
        private const val LOG_TAG = "UsbU2FDeviceConnection"
        private const val CHANNEL_BROADCAST = -1
        private const val CMD_PING = 1.toByte()
        private const val CMD_MSG = 3.toByte()
        private const val CMD_INIT = 6.toByte()
        private const val CMD_ERROR = 0x3f.toByte()
        private const val TIMEOUT = 3000L
    }

    internal data class ReceiveRequestClientData(val buffer: ByteBuffer)

    private var channelId: Int? = null
    private val pendingRequests = mutableListOf<UsbRequest>()

    init { for (i in 0..8) enqueueReceivePacketRequest() }

    fun cancelPendingRequests() {
        synchronized(pendingRequests) {
            pendingRequests.forEach { it.close() }
            pendingRequests.clear()
        }
    }

    private fun enqueueSendPacket(data: ByteArray) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "send packet: ${HexString.toHex(data)}")
        }

        UsbRequest().also {
            synchronized(pendingRequests) { pendingRequests.add(it) }

            it.initialize(connection, outputEndpoint)
            it.queue(ByteBuffer.wrap(data))
        }
    }

    private fun enqueueReceivePacketRequest() {
        UsbRequest().also {
            synchronized(pendingRequests) { pendingRequests.add(it) }

            val request = ReceiveRequestClientData(ByteBuffer.allocate(inputEndpoint.maxPacketSize))

            it.initialize(connection, inputEndpoint)
            it.clientData = request
            it.queue(request.buffer)
        }
    }

    private suspend fun receiveResponsePacket(timeout: Long): ByteArray? {
        val end = SystemClock.uptimeMillis() + timeout

        while (true) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "receiveResponsePacket()")
            }

            if (disconnectReporter.didDisconnect) throw U2FException.DisconnectedException()

            val remaining = end - SystemClock.uptimeMillis(); if (remaining < 0) break
            val response = U2FThread.usb.executeAndWait { connection.requestWait(remaining) } ?: continue

            synchronized(pendingRequests) { pendingRequests.remove(response) }

            response.clientData.let { request ->
                if (request is ReceiveRequestClientData) {
                    enqueueReceivePacketRequest()

                    request.buffer.rewind()

                    return ByteArray(request.buffer.remaining())
                        .also { request.buffer.get(it) }
                        .also {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "got response packet: ${HexString.toHex(it)}")
                            }
                        }
                }
            }
        }

        return null
    }

    private fun sendRequest(
        channelId: Int,
        command: Byte,
        payload: ByteArray
    ) {
        if (payload.size >= UShort.MAX_VALUE.toInt()) throw U2FException.CommunicationException()

        val messageSize = outputEndpoint.maxPacketSize; if (messageSize < 8) throw IllegalStateException()
        val maxPayloadSize = messageSize - 7 + 128 * (messageSize - 5)

        if (payload.size > maxPayloadSize) throw U2FException.CommunicationException()

        var payloadOffset = 0

        kotlin.run { // initial package
            val buffer = ByteArray(messageSize)

            buffer[0] = channelId.ushr(24).toByte()
            buffer[1] = channelId.ushr(16).toByte()
            buffer[2] = channelId.ushr(8).toByte()
            buffer[3] = channelId.toByte()

            buffer[4] = (command.toInt() or 0x80).toByte() // add init flag

            buffer[5] = payload.size.ushr(8).toByte()
            buffer[6] = payload.size.toByte()

            payloadOffset = (messageSize - 7).coerceAtMost(payload.size)

            payload.copyInto(
                destination = buffer,
                destinationOffset = 7,
                startIndex = 0,
                endIndex = payloadOffset
            )

            enqueueSendPacket(buffer)
        }

        var sequenceCounter = 0; while (payloadOffset < payload.size) {
            val buffer = ByteArray(messageSize)

            buffer[0] = channelId.ushr(24).toByte()
            buffer[1] = channelId.ushr(16).toByte()
            buffer[2] = channelId.ushr(8).toByte()
            buffer[3] = channelId.toByte()

            if (sequenceCounter < 0 || sequenceCounter > 0x7f) throw IllegalStateException()
            buffer[4] = sequenceCounter++.toByte()

            val consumedBytes = (messageSize - 5).coerceAtMost(payload.size - payloadOffset)

            payload.copyInto(
                destination = buffer,
                destinationOffset = 5,
                startIndex = payloadOffset,
                endIndex = payloadOffset + consumedBytes
            )

            payloadOffset += consumedBytes

            enqueueSendPacket(buffer)
        }
    }

    internal data class Response(val channelId: Int, val command: Byte, val payload: ByteArray)

    private suspend fun receiveResponse(timeout: Long): Response? {
        val end = SystemClock.uptimeMillis() + timeout

        val remaining1 = end - SystemClock.uptimeMillis(); if (remaining1 < 0) return null
        val response = receiveResponsePacket(remaining1) ?: return null

        if (response.size < 8) throw U2FException.CommunicationException()

        val maxPayloadSize = response.size - 7 + 128 * (response.size - 5)

        val channelId =
            (response[3].toUByte().toUInt() or
                    response[2].toUByte().toUInt().shl(8) or
                    response[1].toUByte().toUInt().shl(16) or
                    response[0].toUByte().toUInt().shl(24)
                    ).toInt()

        val command = response[4].toUByte().toInt()
        val payloadSize = response[6].toUByte().toInt() or response[5].toUByte().toInt().shl(8)

        if (command and 0x80 != 0x80) return null // not at the start of a reponse
        if (payloadSize > maxPayloadSize) throw U2FException.CommunicationException()

        val payload = ByteArray(payloadSize)
        var payloadOffset = (response.size - 7).coerceAtMost(payloadSize)

        response.copyInto(
            destination = payload,
            destinationOffset = 0,
            startIndex = 7,
            endIndex = response.size.coerceAtMost(payloadSize + 7)
        )

        var sequenceCounter = 0; while (payloadOffset < payload.size) {
            val remaining2 = end - SystemClock.uptimeMillis(); if (remaining2 < 0) return null
            val response2 = receiveResponsePacket(remaining2) ?: return null

            if (response2.size != response.size) throw U2FException.CommunicationException()

            val channelId2 =
                (response2[3].toUByte().toUInt() or
                        response2[2].toUByte().toUInt().shl(8) or
                        response2[1].toUByte().toUInt().shl(16) or
                        response2[0].toUByte().toUInt().shl(24)
                        ).toInt()

            val command2 = response2[4].toUByte().toInt()

            if (sequenceCounter < 0 || sequenceCounter > 0x7f) throw IllegalStateException()
            val decodedSequenceCounterr = response2[4].toUByte().toInt()

            if (channelId != channelId2) continue
            if (command2 and 0x80 == 0x80) return null // not at a continuation
            if (sequenceCounter++ != decodedSequenceCounterr) return null // broken sequence

            val consumedBytes = (response.size - 5).coerceAtMost(payloadSize - payloadOffset)

            response2.copyInto(
                destination = payload,
                destinationOffset = payloadOffset,
                startIndex = 5,
                endIndex = response.size.coerceAtMost(consumedBytes + 5)
            )

            payloadOffset += consumedBytes
        }

        return Response(
            channelId = channelId,
            command = (command xor 0x80).toByte(),
            payload = payload
        ).also {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "got response: $it")
            }
        }
    }

    private suspend fun sendRequestAndGetResponse(
        channelId: Int,
        command: Byte,
        payload: ByteArray,
        timeout: Long
    ): Response? {
        val end = SystemClock.uptimeMillis() + timeout

        sendRequest(channelId = channelId, command = command, payload = payload)

        while (true) {
            val remaining = end - SystemClock.uptimeMillis(); if (remaining < 0) return null
            val response = receiveResponse(remaining) ?: continue
            if (response.channelId != channelId) continue

            return response
        }
    }

    private suspend fun allocateChannelId(): Int {
        val end = SystemClock.uptimeMillis() + TIMEOUT
        val nonce = ByteArray(8).also { Random().nextBytes(it) }

        sendRequest(
            channelId = CHANNEL_BROADCAST,
            command = CMD_INIT,
            payload = nonce
        )

        while (true) {
            val remaining = end - SystemClock.uptimeMillis(); if (remaining < 0) throw U2FException.CommunicationException()
            val response = receiveResponse(remaining) ?: continue

            if (response.channelId != CHANNEL_BROADCAST) continue
            if (response.command != CMD_INIT) continue
            if (response.payload.size < 17) continue
            if (!response.payload.sliceArray(0 until 8).contentEquals(nonce)) continue

            val channelId =
                (response.payload[11].toUByte().toUInt() or
                        response.payload[10].toUByte().toUInt().shl(8) or
                        response.payload[9].toUByte().toUInt().shl(16) or
                        response.payload[8].toUByte().toUInt().shl(24)
                        ).toInt()

            if (channelId == CHANNEL_BROADCAST) throw U2FException.CommunicationException()

            return channelId
        }
    }

    private suspend fun getOwnChannelId(): Int {
        channelId?.let { return it }

        allocateChannelId().let { channelId = it; return it }
    }

    suspend fun ping(payload: ByteArray) {
        val response = sendRequestAndGetResponse(
            channelId = getOwnChannelId(),
            command = CMD_PING,
            payload = payload,
            timeout = TIMEOUT
        ) ?: throw U2FException.CommunicationException()

        if (response.command != CMD_PING) throw U2FException.CommunicationException()
        if (!response.payload.contentEquals(payload)) throw U2FException.CommunicationException()
    }

    suspend fun ping(length: Int) = ByteArray(length).also {
        Random().nextBytes(it)

        ping(it)
    }

    override suspend fun execute(request: U2FRequest): U2fRawResponse {
        val response = sendRequestAndGetResponse(
            channelId = getOwnChannelId(),
            command = CMD_MSG,
            payload = request.encodeExtended(),
            timeout = TIMEOUT
        ) ?: throw U2FException.CommunicationException()

        if (response.command != CMD_MSG) throw U2FException.CommunicationException()

        return U2fRawResponse.decode(response.payload).also { it.throwIfNoSuccess() }
    }

    override fun close() {
        cancelPendingRequests()
        connection.close()
    }
}