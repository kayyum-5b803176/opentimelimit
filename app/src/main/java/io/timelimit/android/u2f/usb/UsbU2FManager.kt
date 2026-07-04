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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.getSystemService
import io.timelimit.android.BuildConfig
import io.timelimit.android.extensions.registerNotExportedReceiver
import io.timelimit.android.integration.platform.android.PendingIntentIds
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.util.U2FId

class UsbU2FManager (val parent: U2fManager, context: Context) {
    companion object {
        private const val LOG_TAG = "UsbU2FManager"
    }

    private val usbManager = context.getSystemService<UsbManager>()
    private val disconnectReporters = mutableMapOf<String, DisconnectReporter>()

    private val usbConnectionListener = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) handleAddedDevice(intent.usbDevice)
                else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) handleRemovedDevice(intent.usbDevice)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "error handling new/removed device", ex)
                }
            }
        }
    }

    private val permissionResponseAction = U2FId.generate()

    private val permissionResponseListener = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (intent.action == permissionResponseAction) {
                    val device = intent.usbDevice
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "got permission $granted for $device")
                    }

                    permissionRequestManager.reportResult(device, granted)
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "error handling permission response", ex)
                }
            }
        }
    }

    private val permissionResponseIntent = PendingIntent.getBroadcast(
        context,
        PendingIntentIds.U2F_USB_RESPONSE,
        Intent(permissionResponseAction),
        PendingIntentIds.PENDING_INTENT_FLAGS_ALLOW_MUTATION
    )

    private val permissionRequestManager = UsbPermissionRequestManager(sendRequest = {
        usbManager?.requestPermission(it, permissionResponseIntent)
    })

    init {
        context.registerNotExportedReceiver(usbConnectionListener, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
        context.registerNotExportedReceiver(usbConnectionListener, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
        context.registerNotExportedReceiver(permissionResponseListener, IntentFilter(permissionResponseAction))
    }

    private fun handleAddedDevice(device: UsbDevice) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "new device $device")
        }

        val disconnectReporter = DisconnectReporter().also { disconnectReporters[device.deviceName] = it }

        val u2FDevice = UsbU2FDevice.from(device, permissionRequestManager, usbManager!!, disconnectReporter)

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "found u2f device $u2FDevice")
        }

        u2FDevice?.also { parent.dispatchDeviceFound(it) }
    }

    private fun handleRemovedDevice(device: UsbDevice) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "disconnected $device")
        }

        disconnectReporters.remove(device.deviceName)?.reportDisconnect()
        permissionRequestManager.reportResult(device, false)
    }
}