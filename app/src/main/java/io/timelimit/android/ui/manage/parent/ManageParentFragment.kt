/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
 * Copyright <C> 2020 Marcel Voigt
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
package io.timelimit.android.ui.manage.parent


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.map
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.FragmentManageParentBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.child.advanced.timezone.UserTimezoneView
import io.timelimit.android.ui.manage.parent.delete.DeleteParentView
import io.timelimit.android.ui.manage.parent.key.ManageUserKeyView
import io.timelimit.android.ui.manage.parent.limitlogin.ParentLimitLoginView
import io.timelimit.android.ui.manage.parent.password.biometric.ManageUserBiometricAuthView

class ManageParentFragment : Fragment(), FragmentWithCustomTitle {
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val params: ManageParentFragmentArgs by lazy { ManageParentFragmentArgs.fromBundle(requireArguments()) }
    private val parentUser: LiveData<User?> by lazy { logic.database.user().getParentUserByIdLive(params.parentId) }
    private var wereViewsCreated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentManageParentBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)
        val model = ViewModelProviders.of(this).get(ManageParentModel::class.java)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                fragment = this,
                doesSupportAuth = liveDataFromNonNullValue(true),
                authenticatedUser = activity.getActivityViewModel().authenticatedUser,
                shouldHighlight = activity.getActivityViewModel().shouldHighlightAuthenticationButton
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        model.init(
                activityViewModel = activity.getActivityViewModel(),
                parentUserId = params.parentId
        )

        parentUser.observe(this, Observer {
            user ->

            if (user != null) {
                binding.username = user.name
            }
        })

        if (!wereViewsCreated) {
            wereViewsCreated = true

            parentUser.observe(this, Observer {
                user ->

                if (user == null) {
                    navigation.popBackStack()
                }
            })
        }

        DeleteParentView.bind(
                view = binding.deleteParent,
                lifecycleOwner = this,
                model = model.deleteParentModel
        )

        UserTimezoneView.bind(
                view = binding.timezone,
                userId = params.parentId,
                lifecycleOwner = this,
                fragmentManager = parentFragmentManager,
                auth = activity.getActivityViewModel(),
                userEntry = parentUser
        )

        ManageUserKeyView.bind(
                view = binding.userKey,
                lifecycleOwner = viewLifecycleOwner,
                userId = params.parentId,
                auth = activity.getActivityViewModel(),
                fragmentManager = parentFragmentManager
        )

        ParentLimitLoginView.bind(
                view = binding.parentLimitLogin,
                lifecycleOwner = viewLifecycleOwner,
                userId = params.parentId,
                auth = activity.getActivityViewModel(),
                fragmentManager = parentFragmentManager
        )

        ManageUserBiometricAuthView.bind(
            view = binding.biometricAuth,
            user = parentUser,
            auth = activity.getActivityViewModel(),
            fragmentManager = parentFragmentManager,
            fragment = this
        )

        binding.handlers = object: ManageParentFragmentHandlers {
            override fun onChangePasswordClicked() {
                navigation.safeNavigate(
                        ManageParentFragmentDirections.
                                actionManageParentFragmentToChangeParentPasswordFragment(
                                        params.parentId
                                ),
                        R.id.manageParentFragment
                )
            }

            override fun onManageU2FClicked() {
                navigation.safeNavigate(
                    ManageParentFragmentDirections.
                    actionManageParentFragmentToManageParentU2FKeyFragment(
                        params.parentId
                    ),
                    R.id.manageParentFragment
                )
            }
        }

        return binding.root
    }

    override fun getCustomTitle() = parentUser.map { "${it?.name} < ${getString(R.string.main_tab_overview)}" as String? }
}

interface ManageParentFragmentHandlers {
    fun onChangePasswordClicked()
    fun onManageU2FClicked()
}
