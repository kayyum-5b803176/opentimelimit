/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

package io.timelimit.android.ui.manage.child.advanced.userflagswitch

import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import io.timelimit.android.data.model.User
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.sync.actions.UpdateUserFlagsAction
import io.timelimit.android.ui.main.ActivityViewModel

object UserFlagSwitch {
    fun bind(
            enableSwitch: SwitchCompat,
            flag: Long,
            userEntry: LiveData<User?>,
            lifecycleOwner: LifecycleOwner,
            auth: ActivityViewModel,
            userId: String
    ) {
        userEntry.map { it != null && it.flags and flag == flag }.ignoreUnchanged().observe(lifecycleOwner, Observer { checked ->
            enableSwitch.setOnCheckedChangeListener { _, _ -> /* ignore */ }
            enableSwitch.isChecked = checked
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != checked) {
                    if (
                            !auth.tryDispatchParentAction(
                                    UpdateUserFlagsAction(
                                            userId = userId,
                                            modifiedBits = flag,
                                            newValues = if (isChecked) flag else 0
                                    )
                            )
                    ) {
                        enableSwitch.isChecked = checked
                    }
                }
            }
        })
    }
}