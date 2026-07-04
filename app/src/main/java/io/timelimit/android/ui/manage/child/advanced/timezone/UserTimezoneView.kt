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
package io.timelimit.android.ui.manage.child.advanced.timezone

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.UserTimezoneViewBinding
import io.timelimit.android.ui.main.ActivityViewModel
import java.util.*

object UserTimezoneView {
    fun bind(
            userEntry: LiveData<User?>,
            view: UserTimezoneViewBinding,
            fragmentManager: FragmentManager,
            lifecycleOwner: LifecycleOwner,
            auth: ActivityViewModel,
            userId: String
    ) {
        userEntry.observe(lifecycleOwner, Observer {
            view.timezone = TimeZone.getTimeZone(it?.timeZone ?: "").displayName
        })

        view.changeTimezoneButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                SetUserTimezoneDialogFragment.newInstance(
                        userId = userId
                ).show(fragmentManager)
            }
        }
    }
}