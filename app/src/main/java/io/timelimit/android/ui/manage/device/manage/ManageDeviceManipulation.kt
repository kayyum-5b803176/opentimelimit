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
package io.timelimit.android.ui.manage.device.manage

import android.view.ViewGroup
import android.widget.CheckBox
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.HadManipulationFlag
import io.timelimit.android.data.model.ManipulationFlag
import io.timelimit.android.databinding.ManageDeviceManipulationViewBinding
import io.timelimit.android.sync.actions.IgnoreManipulationAction
import io.timelimit.android.ui.main.ActivityViewModel

object ManageDeviceManipulation {
    fun bindView(
            binding: ManageDeviceManipulationViewBinding,
            deviceEntry: LiveData<Device?>,
            lifecycleOwner: LifecycleOwner,
            activityViewModel: ActivityViewModel,
            status: ManageDeviceManipulationStatus
    ) {
        val selectedCurrent = status.selectedCurrent
        val selectedPast = status.selectedPast

        val currentWarnings = deviceEntry.map { device ->
            if (device == null) {
                ManipulationWarnings.empty
            } else {
                ManipulationWarnings.getFromDevice(device)
            }
        }

        currentWarnings.observe(lifecycleOwner, Observer { warnings ->
            binding.hasAnyManipulation = !warnings.isEmpty

            binding.currentManipulations.removeAllViews()
            binding.pastManipulations.removeAllViews()

            fun createCheckbox() = CheckBox(binding.root.context)
            fun bindList(container: ViewGroup, entries: List<ManipulationWarningType>, selection: MutableList<ManipulationWarningType>) {
                container.removeAllViews()

                entries.forEach { warning ->
                    container.addView(
                            createCheckbox().apply {
                                setText(warning.labelResourceId)
                                isChecked = selection.contains(warning)

                                setOnCheckedChangeListener { _, newIsChecked ->
                                    if (newIsChecked) {
                                        if (activityViewModel.requestAuthenticationOrReturnTrue()) {
                                            selection.add(warning)
                                        } else {
                                            isChecked = false
                                        }
                                    } else {
                                        selection.remove(warning)
                                    }
                                }
                            }
                    )
                }

                selection.removeAll { !entries.contains(it) }
            }

            val visiblePastWarnings = warnings.past.filterNot { warnings.current.contains(it) }

            bindList(binding.currentManipulations, warnings.current, selectedCurrent)
            bindList(binding.pastManipulations, visiblePastWarnings, selectedPast)

            binding.hasCurrentlyManipulation = warnings.current.isNotEmpty()
            binding.hadManipulationInPast = visiblePastWarnings.isNotEmpty()
        })

        binding.ignoreWarningsBtn.setOnClickListener {
            val device = deviceEntry.value ?: return@setOnClickListener
            val warnings = ManipulationWarnings.getFromDevice(device)

            val action = ManipulationWarnings(
                    current = selectedCurrent,
                    past = warnings.both.intersect(selectedCurrent).toList() + selectedPast
            ).buildIgnoreAction(device.id)

            if (action.isEmpty) {
                Snackbar.make(
                        binding.root,
                        R.string.manage_device_manipulation_toast_nothing_selected,
                        Snackbar.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            activityViewModel.tryDispatchParentAction(action)
        }
    }
}

data class ManipulationWarnings(val current: List<ManipulationWarningType>, val past: List<ManipulationWarningType>) {
    companion object {
        val empty = ManipulationWarnings(current = emptyList(), past = emptyList())

        fun getFromDevice(device: Device): ManipulationWarnings {
            val current = mutableListOf<ManipulationWarningType>()
            val past = mutableListOf<ManipulationWarningType>()

            val manipulationFlags = device.hadManipulationFlags
            fun isFlagSet(flag: Long) = (manipulationFlags and flag) == flag

            if (device.manipulationTriedDisablingDeviceAdmin) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.TriedDisablingDeviceAdmin))
            }

            if (device.manipulationOfAppVersion) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.AppDowngrade))
            }
            if (isFlagSet(HadManipulationFlag.APP_VERSION)) {
                past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.AppDowngrade))
            }

            if (device.manipulationOfProtectionLevel) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.DeviceAdmin))
            }
            if (isFlagSet(HadManipulationFlag.PROTECTION_LEVEL)) {
                past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.DeviceAdmin))
            }

            if (device.manipulationOfUsageStats) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.UsageStats))
            }
            if (isFlagSet(HadManipulationFlag.USAGE_STATS_ACCESS)) {
                past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.UsageStats))
            }

            if (device.manipulationOfNotificationAccess) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.NotificationAccess))
            }
            if (isFlagSet(HadManipulationFlag.NOTIFICATION_ACCESS)) {
                past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.NotificationAccess))
            }

            if (device.manipulationOfOverlayPermission) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.OverlayPermission))
            }
            if (isFlagSet(HadManipulationFlag.OVERLAY_PERMISSION)) {
                past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.OverlayPermission))
            }

            if (device.wasAccessibilityServiceEnabled) {
                if (!device.accessibilityServiceEnabled) {
                    current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.AccessibilityService))
                }
            }
            if (isFlagSet(HadManipulationFlag.ACCESSIBILITY_SERVICE)) {
                past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.AccessibilityService))
            }

            if (device.manipulationDidReboot) {
                current.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.DidReboot))
            }

            if (device.hadManipulation) {
                if (past.isEmpty()) {
                    past.add(ManipulationWarningType.Classic(ClassicManipulationWarningType.Unknown))
                }
            }

            if (device.manipulationFlags != 0L) {
                var remainingFlags = device.manipulationFlags

                if (remainingFlags and ManipulationFlag.USED_FGS_KILLER == ManipulationFlag.USED_FGS_KILLER) {
                    past.add(ManipulationWarningType.Flag(ManipulationFlag.USED_FGS_KILLER, R.string.manage_device_manipulation_fgs_killer))

                    remainingFlags = remainingFlags.and(ManipulationFlag.USED_FGS_KILLER.inv())
                }

                if (remainingFlags != 0L) {
                    past.add(ManipulationWarningType.Flag(remainingFlags, R.string.manage_device_manipulation_unknown))
                }
            }

            return ManipulationWarnings(
                    current = current,
                    past = past
            )
        }
    }

    val both = current.intersect(past)
    val isEmpty = current.isEmpty() and past.isEmpty()

    fun buildIgnoreAction(deviceId: String): IgnoreManipulationAction {
        var ignoreHadManipulationFlags = 0L
        var ignoreManipulationFlags = 0L

        past.forEach { type ->
            when (type) {
                is ManipulationWarningType.Classic -> {
                    ignoreHadManipulationFlags = ignoreHadManipulationFlags or when(type.type) {
                        ClassicManipulationWarningType.TriedDisablingDeviceAdmin -> throw IllegalArgumentException()
                        ClassicManipulationWarningType.AppDowngrade -> HadManipulationFlag.APP_VERSION
                        ClassicManipulationWarningType.DeviceAdmin -> HadManipulationFlag.PROTECTION_LEVEL
                        ClassicManipulationWarningType.UsageStats -> HadManipulationFlag.USAGE_STATS_ACCESS
                        ClassicManipulationWarningType.NotificationAccess -> HadManipulationFlag.NOTIFICATION_ACCESS
                        ClassicManipulationWarningType.OverlayPermission -> HadManipulationFlag.OVERLAY_PERMISSION
                        ClassicManipulationWarningType.AccessibilityService -> HadManipulationFlag.ACCESSIBILITY_SERVICE
                        ClassicManipulationWarningType.DidReboot -> throw IllegalArgumentException()
                        ClassicManipulationWarningType.Unknown -> 0L   // handled at an other location
                    }
                }
                is ManipulationWarningType.Flag -> {
                    ignoreManipulationFlags = ignoreManipulationFlags or type.mask
                }
            }.let {/* require handling all cases */}
        }

        val currentClassic = current.filterIsInstance<ManipulationWarningType.Classic>().map { it.type }

        return IgnoreManipulationAction(
                deviceId = deviceId,
                ignoreUsageStatsAccessManipulation = currentClassic.contains(ClassicManipulationWarningType.UsageStats),
                ignoreNotificationAccessManipulation = currentClassic.contains(ClassicManipulationWarningType.NotificationAccess),
                ignoreDeviceAdminManipulationAttempt = currentClassic.contains(ClassicManipulationWarningType.TriedDisablingDeviceAdmin),
                ignoreDeviceAdminManipulation = currentClassic.contains(ClassicManipulationWarningType.DeviceAdmin),
                ignoreOverlayPermissionManipulation = currentClassic.contains(ClassicManipulationWarningType.OverlayPermission),
                ignoreAccessibilityServiceManipulation = currentClassic.contains(ClassicManipulationWarningType.AccessibilityService),
                ignoreAppDowngrade = currentClassic.contains(ClassicManipulationWarningType.AppDowngrade),
                ignoreReboot = currentClassic.contains(ClassicManipulationWarningType.DidReboot),
                ignoreHadManipulation = currentClassic.contains(ClassicManipulationWarningType.Unknown),
                ignoreHadManipulationFlags = ignoreHadManipulationFlags,
                ignoreManipulationFlags = ignoreManipulationFlags
        )
    }
}

sealed class ManipulationWarningType {
    abstract val labelResourceId: Int

    data class Classic(val type: ClassicManipulationWarningType): ManipulationWarningType() {
        override val labelResourceId = when (type) {
            ClassicManipulationWarningType.TriedDisablingDeviceAdmin -> R.string.manage_device_manipulation_device_admin_disable_attempt
            ClassicManipulationWarningType.AppDowngrade -> R.string.manage_device_manipulation_app_version
            ClassicManipulationWarningType.DeviceAdmin -> R.string.manage_device_manipulation_device_admin_disabled
            ClassicManipulationWarningType.UsageStats -> R.string.manage_device_manipulation_usage_stats_access
            ClassicManipulationWarningType.NotificationAccess -> R.string.manage_device_manipulation_notification_access
            ClassicManipulationWarningType.OverlayPermission -> R.string.manage_device_manipulation_overlay_permission
            ClassicManipulationWarningType.AccessibilityService -> R.string.manage_device_manipulation_accessibility_service
            ClassicManipulationWarningType.DidReboot -> R.string.manage_device_manipulation_reboot
            ClassicManipulationWarningType.Unknown -> R.string.manage_device_manipulation_existed
        }
    }

    data class Flag(val mask: Long, override val labelResourceId: Int): ManipulationWarningType()
}

enum class ClassicManipulationWarningType {
    TriedDisablingDeviceAdmin,
    AppDowngrade,
    DeviceAdmin,
    UsageStats,
    NotificationAccess,
    OverlayPermission,
    AccessibilityService,
    DidReboot,
    Unknown
}

class ManageDeviceManipulationStatus {
    val selectedCurrent = mutableListOf<ManipulationWarningType>()
    val selectedPast = mutableListOf<ManipulationWarningType>()
}

class ManageDeviceManipulationStatusModel: ViewModel() {
    val data = ManageDeviceManipulationStatus()
}