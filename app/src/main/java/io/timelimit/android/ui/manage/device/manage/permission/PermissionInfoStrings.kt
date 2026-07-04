/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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

import io.timelimit.android.R
import io.timelimit.android.integration.platform.SystemPermission

data class PermissionInfoStrings (val title: Int, val text: Int) {
    companion object {
        fun getFor(permission: SystemPermission): PermissionInfoStrings = when (permission) {
            SystemPermission.DeviceAdmin -> PermissionInfoStrings(
                title = R.string.manage_device_permission_device_admin_title,
                text = R.string.manage_device_permission_device_admin_text
            )
            SystemPermission.UsageStats -> PermissionInfoStrings(
                title = R.string.manage_device_permissions_usagestats_title,
                text = R.string.manage_device_permissions_usagestats_text
            )
            SystemPermission.Notification -> PermissionInfoStrings(
                title = R.string.manage_device_permission_notification_access_title,
                text = R.string.manage_device_permission_notification_access_text
            )
            SystemPermission.Overlay -> PermissionInfoStrings(
                title = R.string.manage_device_permissions_overlay_title,
                text = R.string.manage_device_permissions_overlay_text
            )
            SystemPermission.AccessibilityService -> PermissionInfoStrings(
                title = R.string.manage_device_permission_accessibility_title,
                text = R.string.manage_device_permission_accessibility_text
            )
        }
    }
}