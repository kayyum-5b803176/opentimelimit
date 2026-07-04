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

package io.timelimit.android.ui.manage.child.advanced.limituserviewing

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserFlags
import io.timelimit.android.databinding.LimitUserViewingViewBinding
import io.timelimit.android.ui.extension.bindHelpDialog
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.manage.child.advanced.userflagswitch.UserFlagSwitch

object LimitUserViewingView {
    fun bind(
            view: LimitUserViewingViewBinding,
            auth: ActivityViewModel,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager,
            userEntry: LiveData<User?>,
            userId: String
    ) {
        view.titleView.bindHelpDialog(
                titleRes = R.string.limit_user_viewing_title,
                textRes = R.string.limit_user_viewing_help,
                fragmentManager = fragmentManager
        )

        UserFlagSwitch.bind(
                enableSwitch = view.enableSwitch,
                userId = userId,
                userEntry = userEntry,
                lifecycleOwner = lifecycleOwner,
                flag = UserFlags.RESTRICT_VIEWING_TO_PARENTS,
                auth = auth
        )
    }
}