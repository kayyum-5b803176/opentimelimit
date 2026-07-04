/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import io.timelimit.android.crypto.Sha512
import io.timelimit.android.integration.platform.NetworkId

class ConnectedNetworkUtil (context: Context) {
    private val workContext = context.applicationContext
    private val wifiManager = workContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getNetworkId(): NetworkId {
        val info: WifiInfo? = wifiManager.connectionInfo

        info ?: return NetworkId.NoNetworkConnected

        val ssid: String? = info.ssid
        val bssid: String? = info.bssid

        if (ssid == null || bssid == null) return NetworkId.NoNetworkConnected
        if (ssid == WifiManager.UNKNOWN_SSID) return NetworkId.MissingPermission

        return NetworkId.Network(Sha512.hashSync(ssid + bssid).substring(0, 16))
    }
}