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
package io.timelimit.android.integration.platform.android

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Small, local-only settings store for the "block apps below X% battery" feature.
 *
 * Deliberately kept out of the main Room database / parent-sync protocol used for
 * categories & time limits - this is a device-local safety feature, not something
 * that needs multi-device sync. If you later want a parent to configure this
 * remotely, move `thresholdPercent`/`enabled` into the existing Device/Config
 * tables and add a matching ParentAction, the same way other device settings work.
 */
class LowBatteryBlockerSettings(context: Context) {
    companion object {
        private const val PREFS_NAME = "low_battery_blocker"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_THRESHOLD = "threshold_percent"

        const val DEFAULT_THRESHOLD_PERCENT = 15

        @Volatile private var instance: LowBatteryBlockerSettings? = null

        // same "shared singleton off the application context" pattern used by
        // ForegroundAppHelper.with(context) elsewhere in this codebase - lets a
        // settings UI reach the same instance the AndroidIntegration created
        fun with(context: Context): LowBatteryBlockerSettings = instance ?: synchronized(this) {
            instance ?: LowBatteryBlockerSettings(context.applicationContext).also { instance = it }
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val enabledLive = MutableLiveData<Boolean>().apply {
        value = prefs.getBoolean(KEY_ENABLED, false)
    }

    private val thresholdLive = MutableLiveData<Int>().apply {
        value = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD_PERCENT)
    }

    val enabled: LiveData<Boolean> = enabledLive
    val thresholdPercent: LiveData<Int> = thresholdLive

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        enabledLive.value = enabled
    }

    fun setThresholdPercent(percent: Int) {
        val clamped = percent.coerceIn(1, 99)

        prefs.edit().putInt(KEY_THRESHOLD, clamped).apply()
        thresholdLive.value = clamped
    }
}
