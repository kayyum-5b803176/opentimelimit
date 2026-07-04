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
package io.timelimit.android.ui.manage.child.advanced

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.FragmentManageChildAdvancedBinding
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.child.advanced.duplicate.DuplicateChildDialogFragment
import io.timelimit.android.ui.manage.child.advanced.limituserviewing.LimitUserViewingView
import io.timelimit.android.ui.manage.child.advanced.managedisabletimelimits.ManageDisableTimelimitsViewHelper
import io.timelimit.android.ui.manage.child.advanced.password.ManageChildPassword
import io.timelimit.android.ui.manage.child.advanced.selflimitadd.ChildSelfLimitAddView
import io.timelimit.android.ui.manage.child.advanced.timezone.UserTimezoneView

class ManageChildAdvancedFragment : Fragment() {
    companion object {
        private const val CHILD_ID = "childId"

        fun newInstance(childId: String) = ManageChildAdvancedFragment().apply {
            arguments = Bundle().apply { putString(CHILD_ID, childId) }
        }
    }

    private val childId: String get() = requireArguments().getString(CHILD_ID)!!
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val childEntry: LiveData<User?> by lazy { logic.database.user().getChildUserByIdLive(childId) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentManageChildAdvancedBinding.inflate(layoutInflater, container, false)

        run {
            // disable time limits

            childEntry.observe(viewLifecycleOwner, Observer {
                child ->

                if (child != null) {
                    binding.disableTimeLimits.handlers = ManageDisableTimelimitsViewHelper.createHandlers(
                            childId = childId,
                            childTimezone = child.timeZone,
                            activity = requireActivity()
                    )
                }
            })

            mergeLiveData(childEntry, liveDataFromFunction { logic.timeApi.getCurrentTimeInMillis() }).map {
                (child, time) ->

                if (time == null || child == null) {
                    null
                } else {
                    ManageDisableTimelimitsViewHelper.getDisabledUntilString(child, time, requireContext())
                }
            }.observe(viewLifecycleOwner, Observer {
                binding.disableTimeLimits.disableTimeLimitsUntilString = it
            })
        }

        UserTimezoneView.bind(
                userEntry = childEntry,
                view = binding.userTimezone,
                fragmentManager = parentFragmentManager,
                lifecycleOwner = this,
                userId = childId,
                auth = auth
        )

        binding.renameChildButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                UpdateChildNameDialogFragment.newInstance(childId).show(parentFragmentManager)
            }
        }

        binding.duplicateChildButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                DuplicateChildDialogFragment.newInstance(childId).show(parentFragmentManager)
            }
        }

        binding.deleteUserButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                DeleteChildDialogFragment.newInstance(childId).show(parentFragmentManager)
            }
        }

        ManageChildPassword.bind(
                view = binding.password,
                childId = childId,
                childEntry = childEntry,
                lifecycleOwner = this,
                auth = auth,
                fragmentManager = parentFragmentManager
        )

        LimitUserViewingView.bind(
                view = binding.limitViewing,
                auth = auth,
                lifecycleOwner = viewLifecycleOwner,
                fragmentManager = parentFragmentManager,
                userEntry = childEntry,
                userId = childId
        )

        ChildSelfLimitAddView.bind(
                view = binding.selfLimitAdd,
                auth = auth,
                lifecycleOwner = viewLifecycleOwner,
                fragmentManager = parentFragmentManager,
                userEntry = childEntry,
                userId = childId
        )

        return binding.root
    }
}
