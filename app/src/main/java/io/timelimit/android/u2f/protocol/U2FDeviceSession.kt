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

import java.io.Closeable

interface U2FDeviceSession: Closeable {
    suspend fun execute(request: U2FRequest): U2fRawResponse
}

suspend fun U2FDeviceSession.register(requeset: U2FRequest.Register) =
    U2FResponse.Register.parse(this.execute(requeset))

suspend fun U2FDeviceSession.login(requeset: U2FRequest.Login) =
    U2FResponse.Login.parse(this.execute(requeset))