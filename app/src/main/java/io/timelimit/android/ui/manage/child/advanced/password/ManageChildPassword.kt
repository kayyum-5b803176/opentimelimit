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
package io.timelimit.android.ui.manage.child.advanced.password

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.ManageChildPasswordBinding
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel

object ManageChildPassword {
    fun bind(
            view: ManageChildPasswordBinding,
            lifecycleOwner: LifecycleOwner,
            childId: String,
            childEntry: LiveData<User?>,
            auth: ActivityViewModel,
            fragmentManager: FragmentManager
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.manage_child_password_title,
                    text = R.string.manage_child_password_info
            ).show(fragmentManager)
        }

        childEntry.observe(lifecycleOwner, Observer {
            view.hasPassword = it?.password?.isNotEmpty() ?: false
        })

        view.setPasswordButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                SetChildPasswordDialogFragment.newInstance(childId).show(fragmentManager)
            }
        }

        view.changePasswordButton.setOnClickListener {
            UpdateChildPasswordDialogFragment.newInstance(childId).show(fragmentManager)
        }
    }
}