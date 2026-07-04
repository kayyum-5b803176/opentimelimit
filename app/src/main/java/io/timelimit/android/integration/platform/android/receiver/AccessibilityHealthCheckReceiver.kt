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
package io.timelimit.android.integration.platform.android.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.timelimit.android.integration.platform.android.AccessibilityService
import io.timelimit.android.integration.platform.android.LowBatteryBlockerSettings
import io.timelimit.android.integration.platform.android.NotificationChannels
import io.timelimit.android.integration.platform.android.NotificationIds
import io.timelimit.android.integration.platform.android.PendingIntentIds
import io.timelimit.android.ui.lowbattery.LowBatteryBlockerSettingsActivity

/**
 * Fires roughly every 15 minutes (see AccessibilityHealthCheckScheduler). Does one
 * cheap check and either posts/updates a warning notification or clears it - no
 * loop, no ongoing work, the receiver returns almost immediately either way.
 */
class AccessibilityHealthCheckReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val settings = LowBatteryBlockerSettings.with(appContext)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // feature turned off -> nothing to check, make sure no stale warning lingers
        if (settings.enabled.value != true) {
            notificationManager.cancel(NotificationIds.ACCESSIBILITY_SERVICE_HEALTH_CHECK)
            return
        }

        val serviceProcessAlive = AccessibilityService.instance != null
        val serviceEnabledInSystemSettings = isAccessibilityServiceEnabledInSystemSettings(appContext)

        val isHealthy = serviceProcessAlive && serviceEnabledInSystemSettings

        if (isHealthy) {
            notificationManager.cancel(NotificationIds.ACCESSIBILITY_SERVICE_HEALTH_CHECK)
            return
        }

        val message = if (!serviceEnabledInSystemSettings) {
            "The accessibility permission got turned off, so low battery app blocking stopped working. Tap to fix it."
        } else {
            // enabled per system settings, but the process isn't alive - a common
            // symptom of OEM battery managers (Xiaomi/Huawei/OnePlus/etc.) silently
            // killing accessibility services in the background
            "Low battery app blocking stopped running in the background. Tap to reopen the app and fix it."
        }

        NotificationChannels.createNotificationChannels(notificationManager, appContext)

        val contentIntent = PendingIntent.getActivity(
            appContext,
            PendingIntentIds.ACCESSIBILITY_SERVICE_HEALTH_CHECK_NOTIFICATION,
            Intent(appContext, LowBatteryBlockerSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntentIds.PENDING_INTENT_FLAGS
        )

        val notification = NotificationCompat.Builder(appContext, NotificationChannels.ACCESSIBILITY_SERVICE_HEALTH_CHECK)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Low battery app blocker isn't working")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NotificationIds.ACCESSIBILITY_SERVICE_HEALTH_CHECK, notification)
    }

    private fun isAccessibilityServiceEnabledInSystemSettings(context: Context): Boolean {
        val service = context.packageName + "/" + AccessibilityService::class.java.canonicalName

        val accessibilityEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (ex: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled != 1) return false

        val enabledServicesString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        return enabledServicesString?.split(":")?.contains(service) ?: false
    }
}
