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
package io.timelimit.android.u2f.usb

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class UsbPermissionRequestManager (
    private val sendRequest: (UsbDevice) -> Unit
) {
    private val pendingRequests = mutableMapOf<String, MutableList<CancellableContinuation<Boolean>>>()

    fun reportResult(device: UsbDevice, granted: Boolean) {
        synchronized(pendingRequests) { pendingRequests.remove(device.deviceName) }
            ?.forEach { it.resume(granted) }
    }

    suspend fun requestPermission(device: UsbDevice): Boolean {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { removePendingRequest(device, continuation) }

            addPendingRequest(device, continuation)

            sendRequest(device)
        }
    }

    private fun addPendingRequest(device: UsbDevice, listener: CancellableContinuation<Boolean>) = synchronized(pendingRequests) {
        pendingRequests.getOrPut(device.deviceName) { mutableListOf() }.add(listener)
    }

    private fun removePendingRequest(device: UsbDevice, listener: CancellableContinuation<Boolean>) = synchronized(pendingRequests) {
        pendingRequests[device.deviceName]?.remove(listener)

        if (pendingRequests[device.deviceName]?.isEmpty() == true) {
            pendingRequests.remove(device.deviceName)
        }
    }
}