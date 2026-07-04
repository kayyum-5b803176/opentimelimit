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

package io.timelimit.android.ui.manage.parent.limitlogin

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.databinding.ParentLimitLoginViewBinding
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.util.TimeTextUtil

object ParentLimitLoginView {
    fun bind(
            view: ParentLimitLoginViewBinding,
            lifecycleOwner: LifecycleOwner,
            userId: String,
            auth: ActivityViewModel,
            fragmentManager: FragmentManager
    ) {
        val database = auth.logic.database
        val context = view.root.context

        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.parent_limit_login_title,
                    text = R.string.parent_limit_login_help
            ).show(fragmentManager)
        }

        view.changeButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                ParentLimitLoginSelectCategoryDialogFragment.newInstance(userId).show(fragmentManager)
            }
        }

        view.preBlockButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                LimitLoginPreBlockDialogFragment.newInstance(userId = userId).show(fragmentManager)
            }
        }

        database.userLimitLoginCategoryDao().countOtherUsersWithoutLimitLoginCategoryLive(userId).switchMap { otherUsers ->
            database.userLimitLoginCategoryDao().getByParentUserIdLive(userId).map { config ->
                otherUsers to config
            }
        }.observe(lifecycleOwner, Observer { (otherUsers, config) ->
            if (otherUsers == 0L) {
                view.canConfigure = false
                view.status = context.getString(R.string.parent_limit_login_status_needs_other_user)
                view.showPreBlock = false
            } else {
                view.canConfigure = true
                view.status = if (config == null)
                    context.getString(R.string.parent_limit_login_status_disabled)
                else
                    context.getString(R.string.parent_limit_login_status_enabled, config.categoryTitle, config.childTitle)
                view.showPreBlock = config != null
                view.preBlockStatus = config?.let {
                    val duration = it.preBlockDuration

                    if (duration == 0L)
                        context.getString(R.string.parent_limit_login_pre_block_disabled)
                    else
                        context.getString(R.string.parent_limit_login_pre_block_enabled, TimeTextUtil.time(duration.toInt(), context))
                }
            }
        })
    }
}