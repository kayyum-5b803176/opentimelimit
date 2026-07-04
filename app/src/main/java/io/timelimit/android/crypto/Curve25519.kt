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
package io.timelimit.android.crypto

import android.util.Base64
import io.timelimit.android.crypto.Curve25519.PRIVATE_KEY_SIZE
import io.timelimit.android.crypto.Curve25519.PUBLIC_KEY_SIZE
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.curve25519.Curve25519KeyPair

object Curve25519 {
    const val PRIVATE_KEY_SIZE = 32
    const val PUBLIC_KEY_SIZE = 32
    const val SIGNATURE_SIZE = 64

    private val instance: Curve25519 by lazy { org.whispersystems.curve25519.Curve25519.getInstance(org.whispersystems.curve25519.Curve25519.JAVA) }

    fun generateKeyPair() = instance.generateKeyPair().serialize()

    fun getPublicKey(data: ByteArray): ByteArray {
        if (data.size != PRIVATE_KEY_SIZE + PUBLIC_KEY_SIZE) {
            throw IllegalArgumentException()
        }

        return data.copyOfRange(0, PUBLIC_KEY_SIZE)
    }

    fun getPublicKeyId(publicKey: ByteArray): String {
        return Base64.encodeToString(publicKey.copyOfRange(0, 6), Base64.NO_WRAP)
    }

    fun getPrivateKey(data: ByteArray): ByteArray {
        if (data.size != PRIVATE_KEY_SIZE + PUBLIC_KEY_SIZE) {
            throw IllegalArgumentException()
        }

        return data.copyOfRange(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE + PRIVATE_KEY_SIZE)
    }

    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray = instance.calculateSignature(privateKey, message)

    fun validateSignature(publicKey: ByteArray, message: ByteArray, signature: ByteArray) = instance.verifySignature(publicKey, message, signature)
}

fun Curve25519KeyPair.serialize(): ByteArray {
    val private = this.privateKey
    val public = this.publicKey

    if (public.size != PUBLIC_KEY_SIZE || privateKey.size != PRIVATE_KEY_SIZE) {
        throw IllegalStateException()
    }

    return public + private
}