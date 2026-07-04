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
import io.timelimit.android.async.Threads
import io.timelimit.android.extensions.registerNotExportedReceiver
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.integration.platform.android.receiver.AccessibilityHealthCheckScheduler

/**
 * Blocks app usage once the battery drops to/below a configured percentage
 * (and is not charging) - without any polling loop, and only while the device
 * is actually awake and in front of the user.
 *
 * Inputs are push-based:
 *  - battery level/charging state -> BatteryStatusUtil, itself just a
 *    BroadcastReceiver on ACTION_BATTERY_CHANGED (the OS wakes us, we don't ask).
 *  - foreground app -> AccessibilityService.addForegroundPackageListener, which only
 *    fires on TYPE_WINDOW_STATE_CHANGED, i.e. exactly when the user switches apps.
 *  - screen/doze state -> ACTION_SCREEN_ON / ACTION_SCREEN_OFF and, from API 23+,
 *    ACTION_DEVICE_IDLE_MODE_CHANGED. These gate everything else: while the screen
 *    is off or the device is in Doze, there is by definition no foreground app to
 *    block, so we actively skip evaluation and drop any stale overlay instead of
 *    silently relying on "no window events happen anyway".
 *
 * Whenever any of these change, we just re-evaluate the current combined state.
 * There is no coroutine, no delay(), no Handler.postDelayed loop here.
 */
class LowBatteryAppBlocker(
    context: Context,
    private val battery: BatteryStatusUtil,
    private val settings: LowBatteryBlockerSettings,
    private val overlay: OverlayUtil
) {
    private val appContext = context.applicationContext
    private val ownPackageName = appContext.packageName
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var latestBattery: BatteryStatus = BatteryStatus.dummy
    private var latestEnabled: Boolean = false
    private var latestThreshold: Int = LowBatteryBlockerSettings.DEFAULT_THRESHOLD_PERCENT
    private var latestForegroundPackage: String? = null

    // device-awake state; the whole feature is a no-op unless this is true
    private var isDeviceActive: Boolean = powerManager.isInteractive && !isDeviceIdle()

    private fun isDeviceIdle(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) powerManager.isDeviceIdleMode else false

    private fun updateDeviceActiveState() {
        val nowActive = powerManager.isInteractive && !isDeviceIdle()

        if (nowActive != isDeviceActive) {
            isDeviceActive = nowActive
            evaluate()
        }
    }

    init {
        // this fires once immediately with the sticky battery value, then again
        // only on real ACTION_BATTERY_CHANGED broadcasts
        battery.status.observeForever {
            latestBattery = it
            evaluate()
        }

        settings.enabled.observeForever {
            latestEnabled = it
            evaluate()

            if (it) {
                AccessibilityHealthCheckScheduler.schedule(appContext)
            } else {
                AccessibilityHealthCheckScheduler.cancel(appContext)
            }
        }

        settings.thresholdPercent.observeForever {
            latestThreshold = it
            evaluate()
        }

        // registered once for the whole app lifetime; survives the
        // AccessibilityService instance being torn down/recreated by the OS
        AccessibilityService.addForegroundPackageListener { packageName, _ ->
            latestForegroundPackage = packageName

            evaluate()
        }

        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
        }

        appContext.registerNotExportedReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = updateDeviceActiveState()
        }, screenStateFilter)
    }

    private fun evaluate() {
        Threads.mainThreadHandler.post {
            if (!isDeviceActive) {
                // screen off or device dozing: nothing to block, and no point
                // keeping a stale overlay window around either
                overlay.hide()
                return@post
            }

            val foregroundPackage = latestForegroundPackage ?: return@post

            if (foregroundPackage == ownPackageName) return@post

            val batteryTooLow = !latestBattery.charging && latestBattery.level <= latestThreshold
            val shouldBlock = latestEnabled && batteryTooLow

            if (shouldBlock) {
                // kick the blocked app to the background immediately ...
                AccessibilityService.instance?.showHomescreen()

                // ... and show the same blocking overlay used elsewhere in the app,
                // so the user understands *why* they got sent home
                overlay.show()
                overlay.setBlockedElement(foregroundPackage)
            } else {
                overlay.hide()
            }
        }
    }
}
