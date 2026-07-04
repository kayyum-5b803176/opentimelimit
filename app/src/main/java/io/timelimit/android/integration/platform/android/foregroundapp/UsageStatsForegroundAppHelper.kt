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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.timelimit.android.BuildConfig
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
abstract class UsageStatsForegroundAppHelper (context: Context): ForegroundAppHelper() {
    protected val usageStatsManager = context.getSystemService(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) Context.USAGE_STATS_SERVICE else "usagestats") as UsageStatsManager
    protected val packageManager: PackageManager = context.packageManager
    protected val backgroundThread: Executor by lazy { Executors.newSingleThreadExecutor() }
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val packageName = context.packageName

    override fun getPermissionStatus(): RuntimePermissionStatus {
        val appOpsStatus = appOpsManager.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName)
        val packageManagerStatus = packageManager.checkPermission("android.permission.PACKAGE_USAGE_STATS", BuildConfig.APPLICATION_ID)

        val allowedUsingSystemSettings = appOpsStatus == AppOpsManager.MODE_ALLOWED
        val allowedUsingAdb = appOpsStatus == AppOpsManager.MODE_DEFAULT && packageManagerStatus == PackageManager.PERMISSION_GRANTED

        if(allowedUsingSystemSettings || allowedUsingAdb) {
            return RuntimePermissionStatus.Granted
        } else {
            return RuntimePermissionStatus.NotGranted
        }
    }
}