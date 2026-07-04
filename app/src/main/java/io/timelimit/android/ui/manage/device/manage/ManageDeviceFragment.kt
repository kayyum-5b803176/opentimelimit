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
package io.timelimit.android.ui.manage.device.manage

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.FragmentManageDeviceBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.device.manage.feature.ManageDeviceFeaturesFragment
import io.timelimit.android.ui.manage.device.manage.permission.ManageDevicePermissionsFragment

class ManageDeviceFragment : Fragment(), FragmentWithCustomTitle {
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceFragmentArgs by lazy { ManageDeviceFragmentArgs.fromBundle(requireArguments()) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = FragmentManageDeviceBinding.inflate(inflater, container, false)
        val userEntries = logic.database.user().getAllUsersLive()

        ManageDeviceManipulation.bindView(
                binding = binding.manageManipulation,
                deviceEntry = deviceEntry,
                lifecycleOwner = this,
                activityViewModel = auth,
                status = ViewModelProviders.of(this).get(ManageDeviceManipulationStatusModel::class.java).data
        )

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromNonNullValue(true)
        )

        binding.handlers = object: ManageDeviceFragmentHandlers {
            override fun showUserScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDeviceUserFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showPermissionsScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDevicePermissionsFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showFeaturesScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDeviceFeaturesFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showManageScreen() {
                navigation.safeNavigate(
                        ManageDeviceFragmentDirections.actionManageDeviceFragmentToManageDeviceAdvancedFragment(
                                deviceId = args.deviceId
                        ),
                        R.id.manageDeviceFragment
                )
            }

            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack()
            } else {
                val now = logic.timeApi.getCurrentTimeInMillis()

                binding.modelString = device.model
                binding.addedAtString = getString(R.string.manage_device_added_at, DateUtils.getRelativeTimeSpanString(
                        device.addedAt,
                        now,
                        DateUtils.HOUR_IN_MILLIS

                ))
                binding.didAppDowngrade = device.currentAppVersion < device.highestAppVersion
                binding.permissionCardText = ManageDevicePermissionsFragment.getPreviewText(device, context!!)
                binding.featureCardText = ManageDeviceFeaturesFragment.getPreviewText(device, context!!)
            }
        })

        logic.deviceId.observe(this, Observer {
            ownDeviceId ->

            binding.isThisDevice = ownDeviceId == args.deviceId
        })

        ManageDeviceIntroduction.bind(
                view = binding.introduction,
                database = logic.database,
                lifecycleOwner = this
        )

        val userEntry = deviceEntry.switchMap {
            device ->

            userEntries.map { users ->
                users.find { user -> user.id == device?.currentUserId }
            }
        }

        UsageStatsAccessRequiredAndMissing.bind(
                view = binding.usageStatsAccessMissing,
                lifecycleOwner = this,
                device = deviceEntry,
                user = userEntry
        )

        ActivityLaunchPermissionRequiredAndMissing.bind(
                view = binding.activityLaunchPermissionMissing,
                lifecycleOwner = this,
                device = deviceEntry,
                user = userEntry
        )

        userEntry.observe(this, Observer {
            binding.userCardText = it?.name ?: getString(R.string.manage_device_current_user_none)
        })

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = deviceEntry.map { "${it?.name} < ${getString(R.string.main_tab_overview)}" }
}

interface ManageDeviceFragmentHandlers {
    fun showUserScreen()
    fun showPermissionsScreen()
    fun showFeaturesScreen()
    fun showManageScreen()
    fun showAuthenticationScreen()
}
