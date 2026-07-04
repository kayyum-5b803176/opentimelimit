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
package io.timelimit.android.ui.manage.device.manage.feature


import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.ManageDeviceFeaturesFragmentBinding
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class ManageDeviceFeaturesFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        fun getPreviewText(device: Device, context: Context): String {
            val featureLabels = mutableListOf<String>()

            if (device.considerRebootManipulation) {
                featureLabels.add(context.getString(R.string.manage_device_reboot_manipulation_title))
            }

            if (device.enableActivityLevelBlocking) {
                featureLabels.add(context.getString(R.string.manage_device_activity_level_blocking_title))
            }

            return if (featureLabels.isEmpty()) {
                context.getString(R.string.manage_device_feature_summary_none)
            } else {
                featureLabels.joinToString(separator = ", ")
            }
        }
    }

    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceFeaturesFragmentArgs by lazy { ManageDeviceFeaturesFragmentArgs.fromBundle(requireArguments()) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = ManageDeviceFeaturesFragmentBinding.inflate(inflater, container, false)

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromNonNullValue(true)
        )

        // handlers
        binding.handlers = object: ManageDeviceFeaturesFragmentHandlers {
            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        // going back
        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack(R.id.overviewFragment, false)
            }
        })

        // handle reboot as manipulation
        ManageDeviceRebootManipulationView.bind(
                view = binding.deviceRebootManipulation,
                lifecycleOwner = this,
                deviceEntry = deviceEntry,
                auth = auth,
                fragmentManager = parentFragmentManager
        )

        // activity level blocking
        ManageDeviceActivityLevelBlocking.bind(
                view = binding.activityLevelBlocking,
                auth = auth,
                deviceEntry = deviceEntry,
                lifecycleOwner = this,
                fragmentManager = parentFragmentManager
        )

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = deviceEntry.map { "${getString(R.string.manage_device_card_feature_title)} < ${it?.name} < ${getString(R.string.main_tab_overview)}" }
}

interface ManageDeviceFeaturesFragmentHandlers {
    fun showAuthenticationScreen()
}
