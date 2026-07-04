/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import android.content.Context
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.integration.platform.android.AccessibilityService

/**
 * Foreground-app detector that costs nothing per call: it never touches
 * UsageStatsManager. AccessibilityService pushes (packageName, className) exactly
 * when the foreground window changes; this class just remembers the last value and
 * hands it back instantly whenever getForegroundApps() is called, however often
 * that happens (every 100ms in the main loop, or otherwise).
 *
 * This is what actually stops the "UsageStatsService" log spam / continuous
 * queryEvents() calls from the original polling-based detector - not because the
 * loop calling getForegroundApps() runs less often, but because this
 * implementation of it doesn't ask the system anything anymore.
 */
class AccessibilityForegroundAppHelper(context: Context): ForegroundAppHelper() {
    private val appContext = context.applicationContext

    @Volatile private var latest: ForegroundApp? = null

    private val listener: (String, String?) -> Unit = { packageName, className ->
        latest = ForegroundApp(packageName, className)
    }

    init {
        AccessibilityService.addForegroundPackageListener(listener)
    }

    override suspend fun getForegroundApps(queryInterval: Long, experimentalFlags: Long): Set<ForegroundApp> {
        return latest?.let { setOf(it) } ?: emptySet()
    }

    fun isAvailable(): Boolean = AccessibilityService.instance != null

    override fun getPermissionStatus(): RuntimePermissionStatus =
        if (isAvailable()) RuntimePermissionStatus.Granted else RuntimePermissionStatus.NotGranted
}
