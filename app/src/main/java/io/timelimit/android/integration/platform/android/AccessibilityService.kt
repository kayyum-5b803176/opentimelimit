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
package io.timelimit.android.integration.platform.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CopyOnWriteArrayList

class AccessibilityService: AccessibilityService() {
    companion object {
        var instance: io.timelimit.android.integration.platform.android.AccessibilityService? = null

        // Event-driven foreground-app-change hook.
        // Anyone interested in "which app is now in front" subscribes here instead of
        // polling UsageStatsManager. Multiple independent consumers (the core foreground-
        // app detector, the low battery blocker, ...) can subscribe at once - this is a
        // list, not a single overwritable var, specifically so one consumer registering
        // doesn't clobber another's subscription.
        private val foregroundPackageListeners = CopyOnWriteArrayList<(packageName: String, className: String?) -> Unit>()

        fun addForegroundPackageListener(listener: (packageName: String, className: String?) -> Unit) {
            foregroundPackageListeners.add(listener)
        }

        fun removeForegroundPackageListener(listener: (packageName: String, className: String?) -> Unit) {
            foregroundPackageListeners.remove(listener)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        this.serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }

        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()

        // this only fires when the foreground app actually changes - never on a timer
        foregroundPackageListeners.forEach { it(packageName, className) }
    }

    override fun onInterrupt() {
        // ignore
    }

    fun showHomescreen() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}