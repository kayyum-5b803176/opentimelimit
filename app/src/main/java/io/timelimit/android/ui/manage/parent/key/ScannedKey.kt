/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.manage.parent.key

import android.util.Base64
import io.timelimit.android.barcode.BarcodeConstants
import io.timelimit.android.crypto.Curve25519
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ScannedKey(val publicKey: ByteArray, val timestamp: Long) {
    companion object {
        private const val expectedSize = 12 + Curve25519.SIGNATURE_SIZE + Curve25519.PUBLIC_KEY_SIZE

        fun tryDecode(dataString: String): ScannedKey? {
            val data = try {
                Base64.decode(dataString, 0)
            } catch (ex: IllegalArgumentException) {
                return null
            }

            if (data.size != expectedSize) {
                return null
            }

            val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }

            if (buffer.getInt(0) != BarcodeConstants.LOCAL_MODE_MAGIC) {
                return null
            }

            val timestamp = buffer.getLong(4)
            val dataToSign = data.copyOfRange(0, 12)
            val signature = data.copyOfRange(12, 12 + Curve25519.SIGNATURE_SIZE)
            val publicKey = data.copyOfRange(12 + Curve25519.SIGNATURE_SIZE, 12 + Curve25519.SIGNATURE_SIZE + Curve25519.PUBLIC_KEY_SIZE)

            if (!Curve25519.validateSignature(publicKey, dataToSign, signature)) {
                return null
            }

            return ScannedKey(publicKey, timestamp)
        }
    }
}