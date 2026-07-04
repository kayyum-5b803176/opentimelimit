/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.ui.manage.device.manage.advanced

import androidx.fragment.app.FragmentManager
import io.timelimit.android.databinding.ManageDeviceViewBinding
import io.timelimit.android.ui.main.ActivityViewModel

object ManageDevice {
    fun bind(
            view: ManageDeviceViewBinding,
            activityViewModel: ActivityViewModel,
            fragmentManager: FragmentManager,
            deviceId: String
    ) {
        view.renameBtn.setOnClickListener {
            if (activityViewModel.requestAuthenticationOrReturnTrue()) {
                UpdateDeviceTitleDialogFragment.newInstance(deviceId).show(fragmentManager)
            }
        }
    }
}
