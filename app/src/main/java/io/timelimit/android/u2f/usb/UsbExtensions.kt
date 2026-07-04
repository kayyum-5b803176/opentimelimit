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

import android.content.Intent
import android.hardware.usb.*
import android.os.Build

val Intent.usbDevice
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        this.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)!!
    else
        this.getParcelableExtra(UsbManager.EXTRA_DEVICE)!!

val UsbDevice.interfaces
    get() = (0 until this.interfaceCount).map { this.getInterface(it) }

val UsbInterface.endpoints
    get() = (0 until this.endpointCount).map { this.getEndpoint(it) }