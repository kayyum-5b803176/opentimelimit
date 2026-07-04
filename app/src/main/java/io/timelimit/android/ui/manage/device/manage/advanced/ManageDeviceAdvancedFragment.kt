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
package io.timelimit.android.ui.manage.device.manage.advanced


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.ManageDeviceAdvancedFragmentBinding
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class ManageDeviceAdvancedFragment : Fragment(), FragmentWithCustomTitle {
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceAdvancedFragmentArgs by lazy { ManageDeviceAdvancedFragmentArgs.fromBundle(requireArguments()) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ManageDeviceAdvancedFragmentBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)

        val userEntry = deviceEntry.switchMap { device ->
            device?.currentUserId?.let { userId ->
                logic.database.user().getUserByIdLive(userId)
            } ?: liveDataFromNullableValue(null as User?)
        }

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromNonNullValue(true)
        )

        ManageDevice.bind(
                view = binding.manageDevice,
                activityViewModel = auth,
                fragmentManager = parentFragmentManager,
                deviceId = args.deviceId
        )

        ManageDeviceTroubleshooting.bind(
                view = binding.troubleshootingView,
                userEntry = userEntry,
                lifecycleOwner = this
        )

        binding.handlers = object: ManageDeviceAdvancedFragmentHandlers {
            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        deviceEntry.observe(this, Observer { device ->
            if (device == null) {
                navigation.popBackStack(R.id.overviewFragment, false)
            }
        })


        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = deviceEntry.map { "${getString(R.string.manage_device_card_manage_title)} < ${it?.name} < ${getString(R.string.main_tab_overview)}" }
}

interface ManageDeviceAdvancedFragmentHandlers {
    fun showAuthenticationScreen()
}
