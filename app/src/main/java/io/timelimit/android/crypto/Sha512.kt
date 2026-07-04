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

import java.security.MessageDigest

object Sha512 {
    private val messageDigest: MessageDigest by lazy { MessageDigest.getInstance("SHA-512") }

    fun hashSync(data: String): String {
        return HexString.toHex(hashSync(data.toByteArray(charset("UTF-8"))))
    }

    private fun hashSync(data: ByteArray): ByteArray = messageDigest.digest(data)
}
