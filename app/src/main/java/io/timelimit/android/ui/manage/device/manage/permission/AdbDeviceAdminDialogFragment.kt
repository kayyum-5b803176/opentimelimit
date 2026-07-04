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
package io.timelimit.android.ui.manage.device.manage.permission

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.integration.platform.android.AdminReceiver

class AdbDeviceAdminDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AdbDeviceAdminDialogFragment"
        private val GRANT_COMMAND = "adb shell dpm set-active-admin --user current ${BuildConfig.APPLICATION_ID}/${AdminReceiver::class.java.canonicalName}"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(context!!, theme)
            .setTitle(R.string.manage_device_permission_device_admin_title)
            .setMessage(getString(R.string.manage_device_permission_no_ui_device_admin, GRANT_COMMAND))
            .setPositiveButton(R.string.generic_ok, null)
            .create()

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}
