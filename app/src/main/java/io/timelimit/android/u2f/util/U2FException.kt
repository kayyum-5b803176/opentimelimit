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
package io.timelimit.android.u2f.util

sealed class U2FException(message: String): RuntimeException(message) {
    class CommunicationException: U2FException("communication error")
    class DisconnectedException: U2FException("disconnected error")
    class InvalidDataException: U2FException("invalid data")
    class DeviceException(status: UShort): U2FException("device reported error $status")
    class BadKeyHandleException: U2FException("bad key handle")
    class UserInteractionRequired: U2FException("user interaction required")
    class BadRequestLength: U2FException("wrong request length")
}