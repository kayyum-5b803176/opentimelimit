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

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.TriedDisablingDeviceAdminAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

class AdminReceiver: DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)

        DefaultAppLogic.with(context).backgroundTaskLogic.syncDeviceStatusAsync()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)

        DefaultAppLogic.with(context).backgroundTaskLogic.syncDeviceStatusAsync()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        if (DefaultAppLogic.with(context).platformIntegration.getCurrentProtectionLevel() != ProtectionLevel.DeviceOwner) {
            runAsync {
                ApplyActionUtil.applyAppLogicAction(
                    action = TriedDisablingDeviceAdminAction,
                    appLogic = DefaultAppLogic.with(context),
                    ignoreIfDeviceIsNotConfigured = true
                )
            }
        }

        return context.getString(R.string.admin_disable_warning)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)

        DefaultAppLogic.with(context).manipulationLogic.reportManualUnlock()
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)

        DefaultAppLogic.with(context).manipulationLogic.reportManualUnlock()
    }
}
