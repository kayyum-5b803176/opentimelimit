/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.ui.manage.device.manage.permission

import androidx.fragment.app.FragmentActivity
import io.timelimit.android.integration.platform.SystemPermission
import io.timelimit.android.ui.help.HelpDialogFragment

object PermissionInfoHelpDialog {
    fun show(activity: FragmentActivity, permission: SystemPermission): Unit = PermissionInfoStrings.getFor(permission).let { text ->
        HelpDialogFragment.newInstance(
            title = text.title,
            text = text.text
        ).show(activity.supportFragmentManager)
    }
}