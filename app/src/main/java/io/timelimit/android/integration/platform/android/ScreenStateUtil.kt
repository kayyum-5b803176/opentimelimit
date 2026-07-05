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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import io.timelimit.android.extensions.registerNotExportedReceiver
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps the current interactive ("screen on") state cached in memory, driven purely
 * by the ACTION_SCREEN_ON / ACTION_SCREEN_OFF broadcasts.
 *
 * Why this exists: isScreenOn() is asked by the background loop on every single
 * round, 24x7. PowerManager.isInteractive is a binder IPC into system_server each
 * time; this class replaces those tens of thousands of daily IPC calls with a read
 * of a volatile boolean. PowerManager is only consulted once, to seed the initial
 * value.
 *
 * The listeners additionally make the screen state *push-based*: the background
 * loop can go to sleep while the screen is off and be woken the instant the screen
 * comes back on, instead of noticing it on its next polling round.
 */
class ScreenStateUtil(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Volatile
    private var screenOn: Boolean = readScreenStateFromSystem()

    private val listeners = CopyOnWriteArrayList<(isScreenOn: Boolean) -> Unit>()

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        appContext.registerNotExportedReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isOn = intent.action == Intent.ACTION_SCREEN_ON

                screenOn = isOn

                listeners.forEach { it(isOn) }
            }
        }, filter)
    }

    @Suppress("DEPRECATION")
    private fun readScreenStateFromSystem(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) powerManager.isInteractive
        else powerManager.isScreenOn

    fun isScreenOn(): Boolean = screenOn

    fun addListener(listener: (isScreenOn: Boolean) -> Unit) {
        listeners.add(listener)
    }
}
