/*
 * Open TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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

import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.integration.platform.SystemPermission
import io.timelimit.android.integration.platform.SystemPermissionConfirmationLevel
import io.timelimit.android.integration.platform.android.AdminReceiver
import io.timelimit.android.logic.DefaultAppLogic

class InformAboutDeviceOwnerDialogFragment: DialogFragment() {
    companion object {
        private const val TAG = "InformAboutDeviceOwnerDialogFragment"

        val shouldShow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
            .setTitle(R.string.inform_about_device_owner_title)
            .setMessage(R.string.inform_about_device_owner_text)
            .setPositiveButton(R.string.inform_about_device_owner_continue) { _, _ ->
                DefaultAppLogic.with(requireContext()).platformIntegration.openSystemPermissionScren(
                    requireActivity(),
                    SystemPermission.DeviceAdmin,
                    SystemPermissionConfirmationLevel.Suggestion
                )
            }
            .setNegativeButton(R.string.generic_cancel, null)
            .create()

    fun show(manager: FragmentManager) {
        showSafe(manager, TAG)
    }
}
