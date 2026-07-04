/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.util

import android.content.Context
import io.timelimit.android.R

object TimeTextUtil {
    private const val millisecondsPerDay = 1000 * 60 * 60 * 24

    fun hours(hours: Int, context: Context): String {
        return context.resources.getQuantityString(R.plurals.util_time_hours, hours, hours)
    }

    fun minutes(minutes: Int, context: Context): String {
        return context.resources.getQuantityString(R.plurals.util_time_minutes, minutes, minutes)
    }

    fun seconds(seconds: Int, context: Context): String {
        return context.resources.getQuantityString(R.plurals.util_time_seconds, seconds, seconds)
    }

    fun days(days: Int, context: Context): String {
        return context.resources.getQuantityString(R.plurals.util_time_days, days, days)
    }

    fun time(time: Int, context: Context): String {
        if (time == 0) {
            return context.resources.getString(R.string.util_time_blocked)
        } else if (time % millisecondsPerDay == 0) {
            return days(time / millisecondsPerDay, context)
        } else {
            val totalMinutes = time / (1000 * 60)
            val minutes = totalMinutes % 60
            val hours = totalMinutes / 60

            return if (minutes != 0 && hours != 0) {
                context.resources.getString(R.string.util_limit_hours_and_minutes, hours(hours, context), minutes(minutes, context))
            } else if (minutes != 0) {
                minutes(minutes, context)
            } else if (hours != 0) {
                hours(hours, context)
            } else {
                context.resources.getString(R.string.util_time_less_minute)
            }
        }
    }

    fun remaining(time: Int, context: Context): String {
        return if (time > 0) {
            context.resources.getString(R.string.util_time_remaining, time(time, context))
        } else {
            context.resources.getString(R.string.util_time_done)
        }
    }

    fun pauseIn(time: Int, context: Context): String {
        return if (time <= 1000 * 60) {
            context.getString(R.string.util_time_pause_shortly)
        } else {
            context.getString(R.string.util_time_pause_in, time(time, context))
        }
    }

    fun used(time: Int, context: Context): String {
        return if (time <= 0) {
            context.resources.getString(R.string.util_time_unused)
        } else {
            context.resources.getString(R.string.util_time_used, time(time, context))
        }
    }
}