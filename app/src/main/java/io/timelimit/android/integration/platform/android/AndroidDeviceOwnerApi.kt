/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.integration.platform.android

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import io.timelimit.android.integration.platform.DeviceOwnerApi

class AndroidDeviceOwnerApi(
    private val componentName: ComponentName,
    private val devicePolicyManager: DevicePolicyManager
): DeviceOwnerApi {
    override fun grantLocationAccess(): Boolean {
        if (VERSION.SDK_INT < VERSION_CODES.M) return false
        if (!devicePolicyManager.isDeviceOwnerApp(componentName.packageName)) return false

        return devicePolicyManager.setPermissionGrantState(
            componentName, componentName.packageName, Manifest.permission.ACCESS_FINE_LOCATION,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        )
    }
}