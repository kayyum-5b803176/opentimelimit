/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.integration.platform.android.foregroundapp.usagestats

import android.app.usage.UsageEvents

class DirectUsageStatsReader (private val events: UsageEvents): UsageStatsReader {
    private val event = UsageEvents.Event()

    companion object {
        private val getInstanceId = try {
            UsageEvents.Event::class.java.getMethod("getInstanceId")
        } catch (ex: NoSuchMethodException) {
            null
        }

        val instanceIdSupported = getInstanceId != null
    }

    override val timestamp: Long get() = event.timeStamp
    override val eventType: Int get() = event.eventType
    override val instanceId: Int get() = if (getInstanceId != null) getInstanceId.invoke(event) as Int
    else throw InstanceIdUnsupportedException()
    override val packageName: String get() = event.packageName
    override val className: String get() = event.className ?: "null"

    override fun loadNextEvent() = events.getNextEvent(event)

    override fun free() {
        while (events.getNextEvent(event)) {
            // just consume it
        }
    }

    class InstanceIdUnsupportedException: RuntimeException("instance id unsupported")
}