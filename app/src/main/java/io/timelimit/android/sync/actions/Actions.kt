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
package io.timelimit.android.sync.actions

import io.timelimit.android.crypto.HexString
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.*
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.sync.validation.ListValidation

// Tip: [Ctrl] + [A] and [Ctrl] + [Shift] + [Minus] make this file easy to read

/*
 * The actions describe things that happen.
 * The same actions (should) result in the same state if applied in the same order.
 * This actions are used for the remote control and monitoring.
 *
 */

// base types
sealed class Action

sealed class AppLogicAction: Action()
sealed class ParentAction: Action()
sealed class ChildAction: Action()

//
// now the concrete actions
//

data class AddUsedTimeActionVersion2(
        val dayOfEpoch: Int,
        val items: List<AddUsedTimeActionItem>,
        val trustedTimestamp: Long
): AppLogicAction() {
    init {
        if (dayOfEpoch < 0 || trustedTimestamp < 0) {
            throw IllegalArgumentException()
        }

        if (items.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (items.distinctBy { it.categoryId }.size != items.size) {
            throw IllegalArgumentException()
        }
    }
}

data class AddUsedTimeActionItem(
        val categoryId: String, val timeToAdd: Int, val extraTimeToSubtract: Int,
        val additionalCountingSlots: Set<AddUsedTimeActionItemAdditionalCountingSlot>,
        val sessionDurationLimits: Set<AddUsedTimeActionItemSessionDurationLimitSlot>
) {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (timeToAdd < 0) {
            throw IllegalArgumentException()
        }

        if (extraTimeToSubtract < 0) {
            throw IllegalArgumentException()
        }
    }
}

data class AddUsedTimeActionItemAdditionalCountingSlot(val start: Int, val end: Int) {
    init {
        if (start < MinuteOfDay.MIN || end > MinuteOfDay.MAX || start > end) {
            throw IllegalArgumentException()
        }

        if (start == MinuteOfDay.MIN && end == MinuteOfDay.MAX) {
            throw IllegalArgumentException()
        }
    }
}

data class AddUsedTimeActionItemSessionDurationLimitSlot(
        val startMinuteOfDay: Int, val endMinuteOfDay: Int,
        val maxSessionDuration: Int, val sessionPauseDuration: Int
) {
    init {
        if (startMinuteOfDay < MinuteOfDay.MIN || endMinuteOfDay > MinuteOfDay.MAX || startMinuteOfDay > endMinuteOfDay) {
            throw IllegalArgumentException()
        }

        if (maxSessionDuration <= 0 || sessionPauseDuration <= 0) {
            throw IllegalArgumentException()
        }
    }
}

data class InstalledApp(val packageName: String, val title: String, val isLaunchable: Boolean, val recommendation: AppRecommendation)
data class AddInstalledAppsAction(val apps: List<InstalledApp>): AppLogicAction() {
    init {
        ListValidation.assertNotEmptyListWithoutDuplicates(apps.map { it.packageName })
    }
}
data class RemoveInstalledAppsAction(val packageNames: List<String>): AppLogicAction() {
    init {
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }
}

data class AppActivityItem (
        val packageName: String,
        val className: String,
        val title: String
)
data class UpdateAppActivitiesAction(
        // package name to activity class names
        val removedActivities: List<Pair<String, String>>,
        val updatedOrAddedActivities: List<AppActivityItem>
): AppLogicAction() {
    init {
        if (removedActivities.isEmpty() && updatedOrAddedActivities.isEmpty()) {
            throw IllegalArgumentException("empty action")
        }
    }
}
object SignOutAtDeviceAction: AppLogicAction()

data class MarkTaskPendingAction(val taskId: String): AppLogicAction() {
    init { IdGenerator.assertIdValid(taskId) }
}

data class AddCategoryAppsAction(val categoryId: String, val packageNames: List<String>): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }
}
data class RemoveCategoryAppsAction(val categoryId: String, val packageNames: List<String>): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        ListValidation.assertNotEmptyListWithoutDuplicates(packageNames)
    }
}

data class CreateCategoryAction(val childId: String, val categoryId: String, val title: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        IdGenerator.assertIdValid(childId)
    }
}
data class DeleteCategoryAction(val categoryId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}
data class UpdateCategoryTitleAction(val categoryId: String, val newTitle: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}
data class SetCategoryExtraTimeAction(val categoryId: String, val newExtraTime: Long, val extraTimeDay: Int = -1): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (newExtraTime < 0) {
            throw IllegalArgumentException("newExtraTime must be >= 0")
        }

        if (extraTimeDay < -1) {
            throw IllegalArgumentException()
        }
    }
}
data class IncrementCategoryExtraTimeAction(val categoryId: String, val addedExtraTime: Long, val extraTimeDay: Int = -1): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (addedExtraTime <= 0) {
            throw IllegalArgumentException("addedExtraTime must be more than zero")
        }

        if (extraTimeDay < -1) {
            throw IllegalArgumentException()
        }
    }
}
data class UpdateCategoryTemporarilyBlockedAction(val categoryId: String, val blocked: Boolean, val endTime: Long?): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (endTime != null && (!blocked)) {
            throw IllegalArgumentException()
        }
    }
}
data class UpdateCategoryTimeWarningsAction(
    val categoryId: String,
    val enable: Boolean,
    val flags: Int,
    val minutes: Int?
): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (minutes != null) {
            if (minutes < CategoryTimeWarning.MIN || minutes > CategoryTimeWarning.MAX) {
                throw IllegalArgumentException()
            }
        }
    }
}
data class SetCategoryForUnassignedApps(val childId: String, val categoryId: String): ParentAction() {
    // category id can be empty

    init {
        IdGenerator.assertIdValid(childId)

        if (categoryId.isNotEmpty()) {
            IdGenerator.assertIdValid(categoryId)
        }
    }
}
data class SetParentCategory(val categoryId: String, val parentCategory: String): ParentAction() {
    // parent category id can be empty

    init {
        IdGenerator.assertIdValid(categoryId)

        if (parentCategory.isNotEmpty()) {
            IdGenerator.assertIdValid(parentCategory)
        }
    }
}
data class UpdateCategoryBatteryLimit(val categoryId: String, val chargingLimit: Int?, val mobileLimit: Int?): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (chargingLimit != null) {
            if (chargingLimit < 0 || chargingLimit > 100) {
                throw IllegalArgumentException()
            }
        }

        if (mobileLimit != null) {
            if (mobileLimit < 0 || mobileLimit > 100) {
                throw IllegalArgumentException()
            }
        }
    }
}
data class UpdateCategoryFlagsAction(val categoryId: String, val modifiedBits: Long, val newValues: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (modifiedBits or CategoryFlags.ALL != CategoryFlags.ALL || modifiedBits or newValues != modifiedBits) {
            throw IllegalArgumentException()
        }
    }
}

data class UpdateCategorySortingAction(val categoryIds: List<String>): ParentAction() {
    init {
        if (categoryIds.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (categoryIds.distinct().size != categoryIds.size) {
            throw IllegalArgumentException()
        }

        categoryIds.forEach { IdGenerator.assertIdValid(it) }
    }
}

data class AddCategoryNetworkId(val categoryId: String, val itemId: String, val hashedNetworkId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
        IdGenerator.assertIdValid(itemId)
        HexString.assertIsHexString(hashedNetworkId)
        if (hashedNetworkId.length != CategoryNetworkId.ANONYMIZED_NETWORK_ID_LENGTH) throw IllegalArgumentException()
    }
}

data class ResetCategoryNetworkIds(val categoryId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}

data class UpdateCategoryDisableLimitsAction(val categoryId: String, val endTime: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (endTime < 0) { throw IllegalArgumentException() }
    }
}

data class UpdateChildTaskAction(val isNew: Boolean, val taskId: String, val categoryId: String, val taskTitle: String, val extraTimeDuration: Int): ParentAction() {
    init {
        IdGenerator.assertIdValid(taskId)
        IdGenerator.assertIdValid(categoryId)

        if (taskTitle.isEmpty() || taskTitle.length > ChildTask.MAX_TASK_TITLE_LENGTH) throw IllegalArgumentException()
        if (extraTimeDuration <= 0 || extraTimeDuration > ChildTask.MAX_EXTRA_TIME) throw IllegalArgumentException()
    }
}

data class DeleteChildTaskAction(val taskId: String): ParentAction() {
    init { IdGenerator.assertIdValid(taskId) }
}

data class ReviewChildTaskAction(val taskId: String, val ok: Boolean, val time: Long, val day: Int?): ParentAction() {
    init {
        if (time <= 0) throw IllegalArgumentException()
        if (day != null && day < 0) throw IllegalArgumentException()
        IdGenerator.assertIdValid(taskId)
    }
}

// DeviceDao

data class UpdateDeviceStatusAction(
        val newProtectionLevel: ProtectionLevel?,
        val newUsageStatsPermissionStatus: RuntimePermissionStatus?,
        val newNotificationAccessPermission: NewPermissionStatus?,
        val newOverlayPermission: RuntimePermissionStatus?,
        val newAccessibilityServiceEnabled: Boolean?,
        val newAppVersion: Int?,
        val didReboot: Boolean,
        val isQOrLaterNow: Boolean,
        val addedManipulationFlags: Long
): AppLogicAction() {
    companion object {
        val empty = UpdateDeviceStatusAction(
                newProtectionLevel = null,
                newUsageStatsPermissionStatus = null,
                newNotificationAccessPermission = null,
                newOverlayPermission = null,
                newAccessibilityServiceEnabled = null,
                newAppVersion = null,
                didReboot = false,
                isQOrLaterNow = false,
                addedManipulationFlags = 0L
        )
    }

    init {
        if (newAppVersion != null && newAppVersion < 0) {
            throw IllegalArgumentException()
        }
    }
}

data class IgnoreManipulationAction(
        val deviceId: String,
        val ignoreDeviceAdminManipulation: Boolean,
        val ignoreDeviceAdminManipulationAttempt: Boolean,
        val ignoreAppDowngrade: Boolean,
        val ignoreNotificationAccessManipulation: Boolean,
        val ignoreUsageStatsAccessManipulation: Boolean,
        val ignoreOverlayPermissionManipulation: Boolean,
        val ignoreAccessibilityServiceManipulation: Boolean,
        val ignoreReboot: Boolean,
        val ignoreHadManipulation: Boolean,
        val ignoreHadManipulationFlags: Long,
        val ignoreManipulationFlags: Long
): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)
    }

    val isEmpty = (!ignoreDeviceAdminManipulation) &&
            (!ignoreDeviceAdminManipulationAttempt) &&
            (!ignoreAppDowngrade) &&
            (!ignoreNotificationAccessManipulation) &&
            (!ignoreUsageStatsAccessManipulation) &&
            (!ignoreReboot) &&
            (!ignoreHadManipulation) &&
            (ignoreHadManipulationFlags == 0L) &&
            (ignoreManipulationFlags == 0L)
}

object TriedDisablingDeviceAdminAction: AppLogicAction()

data class SetDeviceUserAction(val deviceId: String, val userId: String): ParentAction() {
    // user id can be an empty string
    init {
        IdGenerator.assertIdValid(deviceId)

        if (userId != "") {
            IdGenerator.assertIdValid(userId)
        }
    }
}

data class SetDeviceDefaultUserAction(val deviceId: String, val defaultUserId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)

        if (defaultUserId.isNotEmpty()) {
            IdGenerator.assertIdValid(defaultUserId)
        }
    }
}

data class SetDeviceDefaultUserTimeoutAction(val deviceId: String, val timeout: Int): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)

        if (timeout < 0) {
            throw IllegalArgumentException("can not set a negative default user timeout")
        }
    }
}

data class SetConsiderRebootManipulationAction(val deviceId: String, val considerRebootManipulation: Boolean): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)
    }
}

data class UpdateEnableActivityLevelBlocking(val deviceId: String, val enable: Boolean): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)
    }
}

data class UpdateCategoryBlockedTimesAction(val categoryId: String, val blockedTimes: ImmutableBitmask): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)
    }
}

data class UpdateCategoryBlockAllNotificationsAction(val categoryId: String, val blocked: Boolean, val blockDelay: Long?): ParentAction() {
    init {
        IdGenerator.assertIdValid(categoryId)

        if (blockDelay != null && blockDelay < 0) {
            throw IllegalArgumentException("blockDelay must be >= 0")
        }
    }
}

data class CreateTimeLimitRuleAction(val rule: TimeLimitRule): ParentAction()

data class UpdateTimeLimitRuleAction(
        val ruleId: String, val dayMask: Byte, val maximumTimeInMillis: Int, val applyToExtraTimeUsage: Boolean,
        val start: Int, val end: Int, val sessionDurationMilliseconds: Int, val sessionPauseMilliseconds: Int,
        val perDay: Boolean, val expiresAt: Long?
): ParentAction() {
    init {
        IdGenerator.assertIdValid(ruleId)

        if (maximumTimeInMillis < 0) {
            throw IllegalArgumentException()
        }

        if (dayMask < 0 || dayMask > (1 or 2 or 4 or 8 or 16 or 32 or 64)) {
            throw IllegalArgumentException()
        }

        if (start < MinuteOfDay.MIN || end > MinuteOfDay.MAX || start > end) {
            throw IllegalArgumentException()
        }

        if (sessionDurationMilliseconds < 0 || sessionPauseMilliseconds < 0) {
            throw IllegalArgumentException()
        }

        if (expiresAt != null && expiresAt <= 0) {
            throw IllegalArgumentException()
        }
    }
}

data class DeleteTimeLimitRuleAction(val ruleId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(ruleId)
    }
}

// UserDao
data class AddUserAction(val name: String, val userType: UserType, val password: String?, val userId: String, val timeZone: String): ParentAction() {
    init {
        if (userType == UserType.Parent) {
            password!!
        }

        IdGenerator.assertIdValid(userId)
    }
}

data class ChangeParentPasswordAction(
        val parentUserId: String,
        val newPassword: String
): ParentAction() {
    init {
        IdGenerator.assertIdValid(parentUserId)

        if (newPassword.isEmpty()) {
            throw IllegalArgumentException("missing required parameter")
        }
    }
}

data class RemoveUserAction(val userId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)
    }
}

data class SetUserDisableLimitsUntilAction(val childId: String, val timestamp: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(childId)

        if (timestamp < 0) {
            throw IllegalArgumentException()
        }
    }
}

data class UpdateDeviceNameAction(val deviceId: String, val name: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(deviceId)

        if (name.isBlank()) {
            throw IllegalArgumentException("new device name must not be blank")
        }
    }
}

data class SetUserTimezoneAction(val userId: String, val timezone: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)

        if (timezone.isBlank()) {
            throw IllegalArgumentException("tried to set timezone to empty")
        }
    }
}

data class SetChildPasswordAction(val childId: String, val newPasswordHash: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(childId)
    }
}

data class RenameChildAction(val childId: String, val newName: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(childId)

        if (newName.isEmpty()) {
            throw IllegalArgumentException("newName must not be empty")
        }
    }
}

data class ResetUserKeyAction(val userId: String): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)
    }
}

data class UpdateUserFlagsAction(val userId: String, val modifiedBits: Long, val newValues: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)

        if (modifiedBits or UserFlags.ALL_FLAGS != UserFlags.ALL_FLAGS || modifiedBits or newValues != modifiedBits) {
            throw IllegalArgumentException()
        }
    }
}

data class UpdateUserLimitLoginCategory(val userId: String, val categoryId: String?): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)
        categoryId?.let { IdGenerator.assertIdValid(categoryId) }
    }
}

data class UpdateUserLimitLoginPreBlockDuration(val userId: String, val preBlockDuration: Long): ParentAction() {
    init {
        IdGenerator.assertIdValid(userId)

        if (preBlockDuration < 0 || preBlockDuration > UserLimitLoginCategory.MAX_PRE_BLOCK_DURATION)
            throw IllegalArgumentException()
    }
}

data class AddParentU2FKey(val keyHandle: ByteArray, val publicKey: ByteArray): ParentAction()

data class RemoveParentU2FKey(val publicKey: ByteArray, val keyHandle: ByteArray): ParentAction()

// child actions
object ChildSignInAction: ChildAction()

data class ChildChangePasswordAction(val newPasswordHash: String): ChildAction()