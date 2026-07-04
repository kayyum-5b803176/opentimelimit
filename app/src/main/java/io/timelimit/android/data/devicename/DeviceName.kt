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
package io.timelimit.android.data.devicename

import android.content.Context
import android.os.Build

object DeviceName {
    private val selfMarketingNameLock = Object()
    private var selfMarketingName: String? = null

    fun getDeviceNameSync(context: Context): String {
        if (selfMarketingName == null) {
            synchronized(selfMarketingNameLock) {
                if (selfMarketingName == null) {
                    selfMarketingName = getDeviceNameSync(context, Build.DEVICE, Build.MODEL) ?: Build.MODEL
                }
            }
        }

        return selfMarketingName!!
    }

    private fun getDeviceNameSync(context: Context, device: String, model: String): String? = DeviceNameDatabase.fromAssets(context).getDeviceMarketingName(device, model)
}