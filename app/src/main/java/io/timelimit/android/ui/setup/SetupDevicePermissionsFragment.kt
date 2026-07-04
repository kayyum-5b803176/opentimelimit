/*
 * Open TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.FragmentSetupDevicePermissionsBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.integration.platform.SystemPermission
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.device.manage.permission.PermissionInfoHelpDialog

class SetupDevicePermissionsFragment : Fragment() {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private lateinit var binding: FragmentSetupDevicePermissionsBinding

    lateinit var refreshStatusRunnable: Runnable

    init {
        refreshStatusRunnable = Runnable {
            refreshStatus()

            Threads.mainThreadHandler.postDelayed(refreshStatusRunnable, 2000L)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)

        binding = FragmentSetupDevicePermissionsBinding.inflate(inflater, container, false)

        binding.handlers = object: SetupDevicePermissionsHandlers {
            override fun manageDeviceAdmin() {
                logic.platformIntegration.openSystemPermissionScren(requireActivity(), SystemPermission.DeviceAdmin)
            }

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

            override fun gotoNextStep() {
                navigation.safeNavigate(
                        SetupDevicePermissionsFragmentDirections
                                .actionSetupDevicePermissionsFragmentToSetupLocalModeFragment(),
                        R.id.setupDevicePermissionsFragment
                )
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

        refreshStatus()

        return binding.root
    }

    private fun refreshStatus() {
        val platform = logic.platformIntegration

        binding.notificationAccessPermission = platform.getNotificationAccessPermissionStatus()
        binding.protectionLevel = platform.getCurrentProtectionLevel()
        binding.usageStatsAccess = platform.getForegroundAppPermissionStatus()
        binding.overlayPermission = platform.getDrawOverOtherAppsPermissionStatus(true)
        binding.accessibilityServiceEnabled = platform.isAccessibilityServiceEnabled()
    }

    override fun onResume() {
        super.onResume()

        // this additionally schedules it
        refreshStatusRunnable.run()
    }

    override fun onPause() {
        super.onPause()

        Threads.mainThreadHandler.removeCallbacks(refreshStatusRunnable)
    }
}

interface SetupDevicePermissionsHandlers {
    fun manageDeviceAdmin()
    fun openUsageStatsSettings()
    fun openNotificationAccessSettings()
    fun openDrawOverOtherAppsScreen()
    fun openAccessibilitySettings()
    fun gotoNextStep()
    fun helpUsageStatsAccess()
    fun helpNotificationAccess()
    fun helpDrawOverOtherApps()
    fun helpAccesibility()
}
