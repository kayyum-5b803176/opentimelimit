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
package io.timelimit.android.ui.manage.device.manage.permission

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
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.ManageDevicePermissionsFragmentBinding
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.integration.platform.SystemPermission
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class ManageDevicePermissionsFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        fun getPreviewText(device: Device, context: Context): String {
            val permissionLabels = mutableListOf<String>()

            if (device.currentUsageStatsPermission == RuntimePermissionStatus.Granted) {
                permissionLabels.add(context.getString(R.string.manage_device_permissions_usagestats_title_short))
            }

            if (device.currentNotificationAccessPermission == NewPermissionStatus.Granted) {
                permissionLabels.add(context.getString(R.string.manage_device_permission_notification_access_title))
            }

            if (device.currentProtectionLevel != ProtectionLevel.None) {
                permissionLabels.add(context.getString(R.string.manage_device_permission_device_admin_title))
            }

            if (device.currentOverlayPermission == RuntimePermissionStatus.Granted) {
                permissionLabels.add(context.getString(R.string.manage_device_permissions_overlay_title))
            }

            if (device.accessibilityServiceEnabled) {
                permissionLabels.add(context.getString(R.string.manage_device_permission_accessibility_title))
            }

            return if (permissionLabels.isEmpty()) {
                context.getString(R.string.manage_device_permissions_summary_none)
            } else {
                permissionLabels.joinToString(", ")
            }
        }
    }

    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDevicePermissionsFragmentArgs by lazy { ManageDevicePermissionsFragmentArgs.fromBundle(requireArguments()) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = ManageDevicePermissionsFragmentBinding.inflate(inflater, container, false)

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromNonNullValue(true)
        )

        auth.authenticatedUser.map { it?.type == UserType.Parent }.observe(this, Observer {
            binding.isUserSignedIn = it
        })

        // handlers
        binding.handlers = object: ManageDevicePermissionsFragmentHandlers {
            override fun openUsageStatsSettings() {
                logic.platformIntegration.openSystemPermissionScren(requireActivity(), SystemPermission.UsageStats)
            }

            override fun openNotificationAccessSettings() {
                logic.platformIntegration.openSystemPermissionScren(requireActivity(), SystemPermission.Notification)
            }

            override fun openDrawOverOtherAppsScreen() {
                logic.platformIntegration.openSystemPermissionScren(requireActivity(), SystemPermission.Overlay)
            }

            override fun openAccessibilitySettings() {
                logic.platformIntegration.openSystemPermissionScren(requireActivity(), SystemPermission.AccessibilityService)
            }

            override fun manageDeviceAdmin() {
                logic.platformIntegration.openSystemPermissionScren(requireActivity(), SystemPermission.DeviceAdmin)
            }

            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }

            override fun helpUsageStatsAccess() {
                PermissionInfoHelpDialog.show(requireActivity(), SystemPermission.UsageStats)
            }

            override fun helpNotificationAccess() {
                PermissionInfoHelpDialog.show(requireActivity(), SystemPermission.Notification)
            }

            override fun helpDrawOverOtherApps() {
                PermissionInfoHelpDialog.show(requireActivity(), SystemPermission.Overlay)
            }

            override fun helpAccesibility() {
                PermissionInfoHelpDialog.show(requireActivity(), SystemPermission.AccessibilityService)
            }
        }

        // permissions
        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack(R.id.overviewFragment, false)
            } else {
                binding.usageStatsAccess = device.currentUsageStatsPermission
                binding.notificationAccessPermission = device.currentNotificationAccessPermission
                binding.protectionLevel = device.currentProtectionLevel
                binding.overlayPermission = device.currentOverlayPermission
                binding.accessibilityServiceEnabled = device.accessibilityServiceEnabled
            }
        })


        return binding.root
    }

    override fun onResume() {
        super.onResume()

        logic.backgroundTaskLogic.syncDeviceStatusAsync()
    }

    override fun getCustomTitle(): LiveData<String?> = deviceEntry.map { "${getString(R.string.manage_device_card_permission_title)} < ${it?.name} < ${getString(R.string.main_tab_overview)}" }
}

interface ManageDevicePermissionsFragmentHandlers {
    fun openUsageStatsSettings()
    fun openNotificationAccessSettings()
    fun openDrawOverOtherAppsScreen()
    fun openAccessibilitySettings()
    fun manageDeviceAdmin()
    fun showAuthenticationScreen()
    fun helpUsageStatsAccess()
    fun helpNotificationAccess()
    fun helpDrawOverOtherApps()
    fun helpAccesibility()
}
