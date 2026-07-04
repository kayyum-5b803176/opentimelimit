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

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.core.content.getSystemService
import io.timelimit.android.BuildConfig

object RunInBackgroundPermission {
    private const val OP = "android:run_any_in_background"

    fun trySelfGrant(context: Context): Boolean {
        val devicePolicyManager = context.getSystemService<DevicePolicyManager>() ?: return false

        if (!devicePolicyManager.isDeviceOwnerApp(context.packageName)) return false

        val appOpsService = context.getSystemService<AppOpsManager>() ?: return false

        return try {
            AppOps.setMode(OP, appOpsService, context, AppOps.Mode.Allowed)

            true
        } catch (ex: SecurityException) {
            false
        }
    }
}