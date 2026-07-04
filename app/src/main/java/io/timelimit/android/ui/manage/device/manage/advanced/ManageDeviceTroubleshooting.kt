/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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

import android.text.method.LinkMovementMethod
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.ManageDeviceTroubleshootingViewBinding

object ManageDeviceTroubleshooting {
    fun bind(
            view: ManageDeviceTroubleshootingViewBinding,
            userEntry: LiveData<User?>,
            lifecycleOwner: LifecycleOwner
    ) {
        view.bateryOptimizationsText.movementMethod = LinkMovementMethod.getInstance()

        userEntry.observe(lifecycleOwner, Observer {
            val isChild = it?.type == UserType.Child

            view.showBatteryOptimizations = isChild
            view.showNoChildUser = !isChild
        })
    }
}