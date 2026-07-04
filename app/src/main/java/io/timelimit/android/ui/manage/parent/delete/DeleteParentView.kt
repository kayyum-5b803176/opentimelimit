/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.manage.parent.delete

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import io.timelimit.android.R
import io.timelimit.android.databinding.DeleteParentViewBinding
import io.timelimit.android.livedata.mergeLiveData

object DeleteParentView {
    fun bind(
        view: DeleteParentViewBinding,
        lifecycleOwner: LifecycleOwner,
        model: DeleteParentModel
    ) {
        val context = view.root.context

        model.parentUserLive.observe(lifecycleOwner, Observer { user ->
            view.userName = user?.name
        })

        mergeLiveData(model.statusLive, model.parentUserLive.map { it?.name }).observe(lifecycleOwner, Observer { (status, userName) ->
            view.canDelete = status == Status.Ready
            view.currentStatus = when (status) {
                Status.NotAuthenticated -> context.getString(R.string.manage_parent_remove_user_status_not_authenticated, userName)
                Status.WrongAccount -> context.getString(R.string.manage_parent_remove_user_status_wrong_account, userName)
                Status.Ready -> context.getString(R.string.manage_parent_remove_user_status_ready, userName)
                Status.LastWihtoutLoginLimit -> context.getString(R.string.manage_parent_remove_user_status_last_without_login_limit)
                null -> ""
            }
        })

        model.isWorking.observe(lifecycleOwner, Observer { view.isWorking = it })

        view.confirmDeleteButton.setOnClickListener {
            model.deleteUser(view.userPasswordField.text.toString())
            view.userPasswordField.setText("")
        }
    }
}
