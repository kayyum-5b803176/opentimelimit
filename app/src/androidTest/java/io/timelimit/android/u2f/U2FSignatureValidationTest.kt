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

import io.timelimit.android.crypto.HexString
import io.timelimit.android.u2f.protocol.U2FResponse
import io.timelimit.android.u2f.protocol.U2fRawResponse
import org.junit.Test
import org.junit.Assert.*

class U2FSignatureValidationTest {
    private val testPublicKey = HexString.fromHex("04d368f1b665bade3c33a20f1e429c7750d5033660c019119d29aa4ba7abc04aa7c80a46bbe11ca8cb5674d74f31f8a903f6bad105fb6ab74aefef4db8b0025e1d")
    private val testApplicationId = "https://gstatic.com/securitykey/a/example.com"
    private val testClientData = "{\"typ\":\"navigator.id.getAssertion\",\"challenge\":\"opsXqUifDriAAmWclinfbS0e-USY0CgyJHe_Otd7z8o\",\"cid_pubkey\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"HzQwlfXX7Q4S5MtCCnZUNBw3RMzPO9tOyWjBqRl4tJ8\",\"y\":\"XVguGFLIZx1fXg3wNqfdbn75hi4-_7-BxhMljw42Ht4\"},\"origin\":\"http://example.com\"}"
    private val testClientDataHash = HexString.fromHex("ccd6ee2e47baef244d49a222db496bad0ef5b6f93aa7cc4d30c4821b3b9dbc57")
    private val testApplicationIdHash = HexString.fromHex("4b0be934baebb5d12d26011b69227fa5e86df94e7d94aa2949a89f2d493992ca")
    private val loginResponse = HexString.fromHex("0100000001304402204b5f0cd17534cedd8c34ee09570ef542a353df4436030ce43d406de870b847780220267bb998fac9b7266eb60e7cb0b5eabdfd5ba9614f53c7b22272ec10047a923f")
    private val defaultResponse = U2FResponse.Login.parse(
        U2fRawResponse(
            status = 0x9000U,
            payload = loginResponse
        )
    )

    @Test
    fun checkApplicationIdHash() {
        assertEquals(HexString.toHex(testApplicationIdHash), HexString.toHex(U2FApplicationId.fromUrl(testApplicationId)))
    }

    @Test
    fun checkClientData() {
        assertEquals(HexString.toHex(testClientDataHash), HexString.toHex(U2FApplicationId.fromUrl(testClientData)))
    }

    @Test
    fun checkValidSignature() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse
        )

        assertEquals(true, valid)
    }

    @Test
    fun checkShorterPublicKeyInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey.sliceArray(0..8),
            response = defaultResponse
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkLongerPublicKeyInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey + byteArrayOf(1, 2, 3),
            response = defaultResponse
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedPublicKeyInvalid1() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey.copyOf().also { it[0] = 2 },
            response = defaultResponse
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedPublicKeyInvalid2() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey.copyOf().also { it[30] = 2 },
            response = defaultResponse
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedPublicKeyInvalid3() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey.copyOf().also { it[60] = 2 },
            response = defaultResponse
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkShorterSignatureInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(signature = defaultResponse.signature.sliceArray(0..8))
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkLongerSignatureDoesNotCrash() {
        // the actual result is implementation dependent

        U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(signature = defaultResponse.signature + byteArrayOf(1, 2, 3))
        )
    }

    @Test
    fun checkModifiedSignatureInvalid1() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(signature = defaultResponse.signature.copyOf().also { it[0] = 2 })
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedSignatureInvalid2() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(signature = defaultResponse.signature.copyOf().also { it[30] = 2 })
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedSignatureInvalid3() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(signature = defaultResponse.signature.copyOf().also { it[60] = 2 })
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedFlagsInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(flags = -1)
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedCounterInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(counter = 1111U)
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedApplicationIdInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash.copyOf().also { it[5] = 5 },
            challenge = testClientDataHash,
            publicKey = testPublicKey,
            response = defaultResponse.copy(counter = 1111U)
        )

        assertEquals(false, valid)
    }

    @Test
    fun checkModifiedChallengeInvalid() {
        val valid = U2FSignatureValidation.validate(
            applicationId = testApplicationIdHash,
            challenge = testClientDataHash.copyOf().also { it[7] = 7 },
            publicKey = testPublicKey,
            response = defaultResponse.copy(counter = 1111U)
        )

        assertEquals(false, valid)
    }
}