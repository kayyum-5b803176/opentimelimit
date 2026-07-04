/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.data

import java.security.SecureRandom

object IdGenerator {
    private const val LENGTH = 6
    private const val CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val random: SecureRandom by lazy { SecureRandom() }

    fun generateId(): String {
        val output = StringBuilder(LENGTH)

        for (i in 1..LENGTH) {
            output.append(CHARS[random.nextInt(CHARS.length)])
        }

        return output.toString()
    }

    private fun isIdValid(id: String): Boolean {
        if (id.length != LENGTH) {
            return false
        }

        for (char in id) {
            if (!CHARS.contains(char)) {
                return false
            }
        }

        return true
    }

    fun assertIdValid(id: String) {
        if (!isIdValid(id)) {
            throw IllegalArgumentException()
        }
    }
}
