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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.timelimit.android.integration.platform.android.PendingIntentIds

/**
 * Schedules a periodic, low-cost check of whether the AccessibilityService that the
 * low-battery app blocker depends on is still actually running.
 *
 * Deliberately NOT a coroutine loop / Handler.postDelayed chain - this uses
 * AlarmManager.setInexactRepeating(), which lets the OS batch this wake-up together
 * with other apps' alarms instead of firing on its own private schedule. At a 15
 * minute cadence this is ~4 wake-ups/hour, vs. the 3,600-36,000/hour the original
 * polling-based foreground-app loop used.
 *
 * "Inexact" is intentional: this is a housekeeping check, not something that needs
 * to fire at a precise moment, so we let the OS pick the most battery-friendly time.
 */
object AccessibilityHealthCheckScheduler {
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AccessibilityHealthCheckReceiver::class.java)

        return PendingIntent.getBroadcast(
            context.applicationContext,
            PendingIntentIds.ACCESSIBILITY_SERVICE_HEALTH_CHECK_ALARM,
            intent,
            PendingIntentIds.PENDING_INTENT_FLAGS
        )
    }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent(appContext)
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(pendingIntent(appContext))
    }
}
