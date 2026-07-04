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
package io.timelimit.android.ui.overview.overview

import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import java.util.*

sealed class OverviewFragmentItem
object OverviewFragmentHeaderUsers: OverviewFragmentItem()
object OverviewFragmentHeaderDevices: OverviewFragmentItem()
data class OverviewFragmentItemDevice(val device: Device, val deviceUser: User?, val isCurrentDevice: Boolean): OverviewFragmentItem() {
    val isMissingRequiredPermission = deviceUser?.type == UserType.Child && (
            device.currentUsageStatsPermission == RuntimePermissionStatus.NotGranted || device.missingPermissionAtQOrLater
            )
}
data class OverviewFragmentItemUser(val user: User, val temporarilyBlocked: Boolean, val limitsTemporarilyDisabled: Boolean): OverviewFragmentItem()
object OverviewFragmentActionAddUser: OverviewFragmentItem()
object OverviewFragmentHeaderIntro: OverviewFragmentItem()
sealed class ShowMoreOverviewFragmentItem: OverviewFragmentItem() {
    object ShowAllUsers: ShowMoreOverviewFragmentItem()
}
data class TaskReviewOverviewItem(val task: ChildTask, val childTitle: String, val categoryTitle: String, val childTimezone: TimeZone): OverviewFragmentItem()