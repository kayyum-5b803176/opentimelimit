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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.timelimit.android.R

object NotificationIds {
    const val APP_STATUS = 1
    const val NOTIFICATION_BLOCKED = 2
    const val REVOKE_TEMPORARILY_ALLOWED_APPS = 3
    const val TIME_WARNING = 4
    const val EXTRA_TIME_STARTED = 5
    const val OPEN_MAIN_APP_WITH_ERROR = 6
    const val ACCESSIBILITY_SERVICE_HEALTH_CHECK = 7
}

object NotificationChannels {
    const val APP_STATUS = "app status"
    const val BLOCKED_NOTIFICATIONS_NOTIFICATION = "notification blocked notification"
    const val TIME_WARNING = "time warning"
    const val TEMP_ALLOWED_APP = "temporarily allowed App"
    const val EXTRA_TIME_STARTED = "extra time started"
    const val ACCESSIBILITY_SERVICE_HEALTH_CHECK = "accessibility service health check"

    private fun createAppStatusChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            APP_STATUS,
                            context.getString(R.string.notification_channel_app_status_title),
                            NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notification_channel_app_status_description)
                        enableLights(false)
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
                    }
            )
        }
    }

    private fun createBlockedNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.BLOCKED_NOTIFICATIONS_NOTIFICATION,
                            context.getString(R.string.notification_channel_blocked_notification_title),
                            NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.notification_channel_blocked_notification_text)
                    }
            )
        }
    }

    private fun createTimeWarningsNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                    NotificationChannel(
                            NotificationChannels.TIME_WARNING,
                            context.getString(R.string.notification_channel_time_warning_title),
                            NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.notification_channel_time_warning_text)
                    }
            )
        }
    }

    private fun createTempAllowedAppChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    TEMP_ALLOWED_APP,
                    context.getString(R.string.notification_channel_apps_temporarily_allowed_title),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_apps_temporarily_allowed_text)
                    enableLights(false)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
                }
            )
        }
    }

    private fun createExtraTimeStartedNotificationChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    EXTRA_TIME_STARTED,
                    context.getString(R.string.notification_channel_extra_time_started_title),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_extra_time_started_description)
                }
            )
        }
    }

    private fun createAccessibilityHealthCheckChannel(notificationManager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    ACCESSIBILITY_SERVICE_HEALTH_CHECK,
                    "Low battery blocker status",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Warns when the accessibility service needed for the low battery app blocker has stopped running"
                }
            )
        }
    }

    fun createNotificationChannels(notificationManager: NotificationManager, context: Context) {
        createAppStatusChannel(notificationManager, context)
        createBlockedNotificationChannel(notificationManager, context)
        createTimeWarningsNotificationChannel(notificationManager, context)
        createTempAllowedAppChannel(notificationManager, context)
        createExtraTimeStartedNotificationChannel(notificationManager, context)
        createAccessibilityHealthCheckChannel(notificationManager, context)
    }
}

object PendingIntentIds {
    const val OPEN_MAIN_APP = 1
    const val REVOKE_TEMPORARILY_ALLOWED = 2
    const val SWITCH_TO_DEFAULT_USER = 3
    const val U2F_NFC_DISCOVERY = 4
    const val U2F_USB_RESPONSE = 5
    const val OPEN_MAIN_APP_WITH_ERROR = 6
    const val ACCESSIBILITY_SERVICE_HEALTH_CHECK_ALARM = 7
    const val ACCESSIBILITY_SERVICE_HEALTH_CHECK_NOTIFICATION = 8

    val PENDING_INTENT_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    val PENDING_INTENT_FLAGS_ALLOW_MUTATION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }
}
