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
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class AccessibilityService: AccessibilityService() {
    companion object {
        var instance: io.timelimit.android.integration.platform.android.AccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.serviceInfo = AccessibilityServiceInfo().apply {
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            }
        }

        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ignore
    }

    override fun onInterrupt() {
        // ignore
    }

    fun showHomescreen() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}