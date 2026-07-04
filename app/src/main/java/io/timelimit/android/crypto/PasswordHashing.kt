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
package io.timelimit.android.crypto

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import org.mindrot.jbcrypt.BCrypt

object PasswordHashing {
    suspend fun hashCoroutine(password: String) = Threads.crypto.executeAndWait {
        hashSync(password)
    }

    fun hashSync(password: String) = hashSyncWithSalt(password, generateSalt())

    fun hashSyncWithSalt(password: String, salt: String): String = BCrypt.hashpw(password, salt)
    fun generateSalt(): String = BCrypt.gensalt()

    fun validateSync(password: String, hashed: String): Boolean {
        try {
            return BCrypt.checkpw(password, hashed)
        } catch (ex: Exception) {
            return false
        }
    }
}
