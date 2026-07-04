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

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.timelimit.android.R
import io.timelimit.android.integration.platform.AppStatusMessage
import io.timelimit.android.logic.DefaultAppLogic

class BackgroundService: Service() {
    companion object {
        private const val EXTRA_NOTIFICATION = "notification"

        fun setStatusMessage(status: AppStatusMessage?, context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, BackgroundService::class.java)

            if (status != null) {
                if (isBackgroundActivityRestricted(context)) {
                    val notification = buildNotification(status, context)

                    notificationManager.notify(NotificationIds.APP_STATUS, notification)
                } else {
                    ContextCompat.startForegroundService(
                            context,
                            intent.putExtra(EXTRA_NOTIFICATION, status)
                    )
                }
            } else {
                context.stopService(intent)

                if (isBackgroundActivityRestricted(context)) {
                    notificationManager.cancel(NotificationIds.APP_STATUS)
                }
            }
        }

        fun isBackgroundActivityRestricted(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (activityManager.isBackgroundRestricted) {
                    if (RunInBackgroundPermission.trySelfGrant(context)) {
                        if (activityManager.isBackgroundRestricted) return true
                    } else return true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!context.getSystemService<DevicePolicyManager>()!!.isAdminActive(ComponentName(context, AdminReceiver::class.java))) {
                    return true
                }
            }

            return false
        }

        private fun buildNotification(appStatusMessage: AppStatusMessage, context: Context) = NotificationCompat.Builder(context, NotificationChannels.APP_STATUS)
            .setSmallIcon(R.drawable.ic_stat_timelapse)
            .setContentTitle(appStatusMessage.title)
            .setContentText(appStatusMessage.text)
            .setSubText(appStatusMessage.subtext)
            .setContentIntent(
                if (appStatusMessage.showErrorMessage)
                    BackgroundActionService.getOpenAppWithErrorIntent(context)
                else
                    BackgroundActionService.getOpenAppIntent(context)
            )
            .setWhen(0)
            .setShowWhen(false)
            .setSound(null)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .let { builder ->
                if (appStatusMessage.showSwitchToDefaultUserOption) {
                    builder.addAction(
                        NotificationCompat.Action.Builder(
                            R.drawable.ic_account_circle_black_24dp,
                            context.getString(R.string.manage_device_default_user_switch_btn),
                            BackgroundActionService.getSwitchToDefaultUserIntent(context)
                        ).build()
                    )
                }

                builder
            }
            .build()
    }

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var didPostNotification = false

    override fun onCreate() {
        super.onCreate()

        // init the app logic if not yet done
        DefaultAppLogic.with(this)

        // create the channel
        NotificationChannels.createNotificationChannels(notificationManager, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val appStatusMessage = intent.getParcelableExtra<AppStatusMessage>(EXTRA_NOTIFICATION)!!

            val notification = buildNotification(appStatusMessage, this)

            if (didPostNotification) {
                notificationManager.notify(NotificationIds.APP_STATUS, notification)
            } else {
                startForeground(NotificationIds.APP_STATUS, notification)
                didPostNotification = true
            }
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        stopForeground(true)
        didPostNotification = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        throw NotImplementedError()
    }
}