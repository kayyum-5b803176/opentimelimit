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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.content.Context
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus

/**
 * Wraps the original UsageStatsManager-based detector (queryUsageStats) with the
 * event-driven AccessibilityService-based one.
 *
 * - While the accessibility service is alive: every getForegroundApps() call is
 *   answered from memory, no UsageStatsManager call happens at all - this is what
 *   fixes the "continuous UsageStatsService queryEvents()" log spam, since it
 *   removes the per-tick system call rather than just changing how often it's
 *   made.
 * - The moment the accessibility service isn't available (permission not granted
 *   yet during onboarding, or an OEM silently killed it - see
 *   AccessibilityHealthCheckReceiver) it transparently falls back to the original
 *   UsageStatsManager-based detector, so nothing regresses functionally; it just
 *   goes back to costing what it always cost.
 *
 * Permission status is intentionally still reported from the underlying
 * UsageStatsManager-based helper: PACKAGE_USAGE_STATS is what the existing setup/
 * onboarding flow checks for, and that requirement doesn't change just because we
 * can sometimes avoid using it at runtime.
 */
class HybridForegroundAppHelper(
    context: Context,
    private val usageStatsBased: ForegroundAppHelper
): ForegroundAppHelper() {
    private val accessibilityBased = AccessibilityForegroundAppHelper(context)

    override suspend fun getForegroundApps(queryInterval: Long, experimentalFlags: Long): Set<ForegroundApp> {
        return if (accessibilityBased.isAvailable()) {
            accessibilityBased.getForegroundApps(queryInterval, experimentalFlags)
        } else {
            usageStatsBased.getForegroundApps(queryInterval, experimentalFlags)
        }
    }

    override fun getPermissionStatus(): RuntimePermissionStatus = usageStatsBased.getPermissionStatus()
}
