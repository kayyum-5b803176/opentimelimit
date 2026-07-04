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

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SignOutAtDeviceAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.diagnose.exception.DiagnoseExceptionActivity

class BackgroundActionService: Service() {
    companion object {
        private const val ACTION = "action"
        private const val ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS = "revoke_temporarily_allowed_apps"
        private const val ACTION_SWITCH_TO_DEFAULT_USER = "switch_to_default_user"

        fun prepareRevokeTemporarilyAllowed(context: Context) = Intent(context, BackgroundActionService::class.java)
                .putExtra(ACTION, ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS)

        fun getSwitchToDefaultUserIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            PendingIntentIds.SWITCH_TO_DEFAULT_USER,
            Intent(context, BackgroundActionService::class.java)
                .putExtra(ACTION, ACTION_SWITCH_TO_DEFAULT_USER),
            PendingIntentIds.PENDING_INTENT_FLAGS
        )

        fun getOpenAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
                context,
                PendingIntentIds.OPEN_MAIN_APP,
                Intent(context, MainActivity::class.java),
                PendingIntentIds.PENDING_INTENT_FLAGS
        )

        fun getOpenAppWithErrorIntent(context: Context): PendingIntent = PendingIntent.getActivities(
            context,
            PendingIntentIds.OPEN_MAIN_APP_WITH_ERROR,
            arrayOf(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Intent(context, DiagnoseExceptionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ),
            PendingIntentIds.PENDING_INTENT_FLAGS
        )
    }

    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()

        // init the app logic if not yet done
        DefaultAppLogic.with(this)

        // create the channel
        NotificationChannels.createNotificationChannels(notificationManager, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.getStringExtra(ACTION)

            if (action == ACTION_REVOKE_TEMPORARILY_ALLOWED_APPS) {
                runAsync {
                    DefaultAppLogic.with(this@BackgroundActionService).backgroundTaskLogic.resetTemporarilyAllowedApps()
                }
            } else if (action == ACTION_SWITCH_TO_DEFAULT_USER) {
                runAsync {
                    val logic = DefaultAppLogic.with(this@BackgroundActionService)

                    ApplyActionUtil.applyAppLogicAction(
                            appLogic = logic,
                            action = SignOutAtDeviceAction,
                            ignoreIfDeviceIsNotConfigured = true
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        throw NotImplementedError()
    }
}
