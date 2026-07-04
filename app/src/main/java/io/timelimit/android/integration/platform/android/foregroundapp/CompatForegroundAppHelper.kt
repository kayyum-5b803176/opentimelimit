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

import android.app.ActivityManager
import android.content.Context
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CompatForegroundAppHelper(context: Context) : ForegroundAppHelper() {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private var lastForegroundApp: ForegroundApp? = null
    private var lastForegroundAppList: Set<ForegroundApp> = emptySet()
    private val mutex = Mutex()

    override suspend fun getForegroundApps(queryInterval: Long, experimentalFlags: Long): Set<ForegroundApp> {
        mutex.withLock {
            try {
                val activity = activityManager.getRunningTasks(1)[0].topActivity!!

                val last = lastForegroundApp

                if (last == null || last.packageName != activity.packageName || last.activityName != activity.className) {
                    val new = ForegroundApp(activity.packageName, activity.className)

                    lastForegroundApp = new
                    lastForegroundAppList = setOf(new)
                }
            } catch (ex: NullPointerException) {
                lastForegroundApp = null
                lastForegroundAppList = emptySet()
            }

            return lastForegroundAppList
        }
    }

    override fun getPermissionStatus(): RuntimePermissionStatus {
        return RuntimePermissionStatus.NotRequired
    }
}
