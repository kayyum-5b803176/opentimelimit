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

import android.content.Context
import androidx.fragment.app.FragmentActivity
import io.timelimit.android.u2f.nfc.NFCU2FManager
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.u2f.usb.UsbU2FManager

class U2fManager (context: Context) {
    companion object {
        private var instance: U2fManager? = null
        private val instanceLock = Object()

        fun with(context: Context): U2fManager {
            if (instance == null) {
                synchronized(instanceLock) {
                    if (instance == null) {
                        instance = U2fManager(context.applicationContext)
                    }
                }
            }

            return instance!!
        }

        fun setupActivity(activity: FragmentActivity) = with(activity).setupActivity(activity)
    }

    private val nfc = NFCU2FManager(this, context)
    private val usb = UsbU2FManager(this, context)

    private val deviceFoundListeners = mutableListOf<DeviceFoundListener>()

    val nfcStatus = nfc.status

    fun registerListener(listener: DeviceFoundListener) {
        if (deviceFoundListeners.contains(listener)) {
            throw IllegalStateException()
        }

        deviceFoundListeners.add(listener)
    }

    fun unregisterListener(listener: DeviceFoundListener) {
        if (!deviceFoundListeners.remove(listener)) {
            throw IllegalStateException()
        }
    }

    fun dispatchDeviceFound(device: U2FDevice) {
        deviceFoundListeners.lastOrNull()?.onDeviceFound(device)
    }

    fun setupActivity(activity: FragmentActivity) {
        nfc.setupActivity(activity)
    }

    interface DeviceFoundListener {
        fun onDeviceFound(device: U2FDevice)
    }
}