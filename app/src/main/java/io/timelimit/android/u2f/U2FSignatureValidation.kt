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
package io.timelimit.android.u2f

import io.timelimit.android.u2f.protocol.U2FResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec


object U2FSignatureValidation {
    fun validate(
        applicationId: ByteArray,
        challenge: ByteArray,
        response: U2FResponse.Login,
        publicKey: ByteArray
    ): Boolean {
        try {
            val signedData =
                applicationId + byteArrayOf(
                    response.flags,
                    response.counter.shr(24).toUByte().toByte(),
                    response.counter.shr(16).toUByte().toByte(),
                    response.counter.shr(8).toUByte().toByte(),
                    response.counter.toUByte().toByte(),
                ) + challenge

            if (publicKey.size != 65 || publicKey[0] != 4.toByte()) return false

            val verifier = Signature.getInstance("SHA256withECDSA")

            verifier.initVerify(
                KeyFactory
                    .getInstance("EC")
                    .generatePublic(
                        X509EncodedKeySpec(
                            byteArrayOf(48, 89, 48, 19, 6, 7, 42, -122, 72, -50, 61, 2, 1, 6, 8, 42, -122, 72, -50, 61, 3, 1, 7, 3, 66, 0) + publicKey
                        )
                    )
            )

            verifier.update(signedData)

            return verifier.verify(response.signature)
        } catch (ex: InvalidKeySpecException) {
            return false
        }
    }
}