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
package io.timelimit.android.ui.manage.device.manage

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.timelimit.android.async.Threads
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.databinding.FragmentManageDeviceIntroductionBinding

object ManageDeviceIntroduction {
    fun bind(
            view: FragmentManageDeviceIntroductionBinding,
            database: Database,
            lifecycleOwner: LifecycleOwner
    ) {
        database.config().wereHintsShown(HintsToShow.DEVICE_SCREEN_INTRODUCTION).observe(lifecycleOwner, Observer { view.showHint = !it })

        view.confirmBtn.setOnClickListener {
            Threads.database.submit {
                database.config().setHintsShownSync(HintsToShow.DEVICE_SCREEN_INTRODUCTION)
            }
        }
    }
}
