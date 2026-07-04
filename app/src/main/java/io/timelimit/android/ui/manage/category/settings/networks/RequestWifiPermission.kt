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
package io.timelimit.android.ui.manage.category.settings.networks

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.timelimit.android.R
import io.timelimit.android.integration.platform.PlatformIntegration

object RequestWifiPermission {
    private fun isLocationEnabled(context: Context): Boolean = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE) == Settings.Secure.LOCATION_MODE_OFF
    else {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationManager.isLocationEnabled
    }

    fun doRequest(fragment: Fragment, permissionRequestCode: Int, platformIntegration: PlatformIntegration) {
        if (ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (platformIntegration.deviceOwner.grantLocationAccess()) return

            fragment.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), permissionRequestCode)
        } else if (!isLocationEnabled(fragment.requireContext())) {
            Toast.makeText(fragment.requireContext(), R.string.category_networks_toast_enable_location_service, Toast.LENGTH_SHORT).show()

            fragment.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            Toast.makeText(fragment.requireContext(), R.string.error_general, Toast.LENGTH_SHORT).show()
        }
    }
}