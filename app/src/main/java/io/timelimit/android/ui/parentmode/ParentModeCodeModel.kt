/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.parentmode

import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.barcode.BarcodeConstants
import io.timelimit.android.barcode.BarcodeMask
import io.timelimit.android.barcode.DataMatrix
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.Database
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.liveDataFromNullableValue
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ParentModeCodeModel: ViewModel() {
    private var didInit = false
    lateinit var barcodeContent: LiveData<BarcodeMask?>
    lateinit var keyId: LiveData<String>

    fun init(database: Database) {
        if (didInit) {
            return
        }

        didInit = true

        val parentKey = database.config().getParentModeKeyLive()
        val timestamp = liveDataFromFunction (5 * 1000L) { System.currentTimeMillis() }

        keyId = parentKey.map { key ->
            if (key != null) {
                Curve25519.getPublicKeyId(Curve25519.getPublicKey(key))
            } else {
                "???"
            }
        }

        barcodeContent = parentKey.switchMap { parentKey ->
            if (parentKey == null) {
                liveDataFromNullableValue(null as BarcodeMask?)
            } else {
                val privateKey = Curve25519.getPrivateKey(parentKey)
                val publicKey = Curve25519.getPublicKey(parentKey)

                timestamp.map { timestamp ->
                    val dataToSign = ByteArray(12)

                    ByteBuffer.allocate(12).apply {
                        order(ByteOrder.BIG_ENDIAN)

                        putInt(0, BarcodeConstants.LOCAL_MODE_MAGIC)
                        putLong(4, timestamp)

                        get(dataToSign)
                    }

                    val signature = Curve25519.sign(
                            privateKey,
                            dataToSign
                    )

                    val content = dataToSign + signature + publicKey

                    DataMatrix.generate(Base64.encodeToString(content, Base64.NO_WRAP)) as BarcodeMask?
                }
            }
        }
    }
}