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
package io.timelimit.android.sync.actions.dispatch

import io.timelimit.android.data.Database
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.extensions.getChildCategories
import io.timelimit.android.data.model.*
import io.timelimit.android.sync.actions.*
import java.util.*

object LocalDatabaseParentActionDispatcher {
    fun dispatchParentActionSync(action: ParentAction, database: Database, fromChildSelfLimitAddChildUserId: String?, parentUserId: String?) {
        if (fromChildSelfLimitAddChildUserId != null) {
            val isSupportedAction = action is CreateTimeLimitRuleAction || action is CreateCategoryAction ||
                    action is UpdateCategoryBlockAllNotificationsAction || action is SetParentCategory ||
                    action is AddCategoryAppsAction || action is UpdateCategoryTemporarilyBlockedAction ||
                    action is UpdateCategoryBlockedTimesAction || action is UpdateCategoryDisableLimitsAction ||
                    action is UpdateTimeLimitRuleAction

            if (!isSupportedAction) {
                throw RuntimeException("unsupported action for the child limit self mode")
            }
        }

        database.runInTransaction {
            when (action) {
                is AddCategoryAppsAction -> {
                    // validate that the category exists
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (categoryEntry.childId != fromChildSelfLimitAddChildUserId) {
                            throw RuntimeException("can not add apps to other users")
                        }
                    }

                    // remove same apps from other categories of the same child
                    val allCategoriesOfChild = database.category().getCategoriesByChildIdSync(categoryEntry.childId)

                    if (fromChildSelfLimitAddChildUserId != null) {
                        val parentCategoriesOfTargetCategory = allCategoriesOfChild.getCategoryWithParentCategories(action.categoryId)
                        val userEntry = database.user().getUserByIdSync(fromChildSelfLimitAddChildUserId) ?: throw RuntimeException("user not found")
                        val validatedDefaultCategoryId = (allCategoriesOfChild.find {
                            it.id == userEntry.categoryForNotAssignedApps
                        })?.id
                        val allowUnassignedElements = parentCategoriesOfTargetCategory.contains(validatedDefaultCategoryId)
                        val userCategoryIds = allCategoriesOfChild.map { it.id }

                        fun assertCanAddApp(packageName: String, isApp: Boolean) {
                            val categoryAppEntry = database.categoryApp().getCategoryAppSync(categoryIds = userCategoryIds, packageName = packageName)

                            if (categoryAppEntry == null) {
                                if ((isApp && allowUnassignedElements) || (!isApp)) {
                                    // allow
                                } else {
                                    throw RuntimeException("can not assign apps without category as child")
                                }
                            } else {
                                if (parentCategoriesOfTargetCategory.contains(categoryAppEntry.categoryId)) {
                                    // allow
                                } else {
                                    throw RuntimeException("can not add app which is not contained in the parent category")
                                }
                            }
                        }

                        action.packageNames.forEach { packageName ->
                            if (packageName.contains(":")) {
                                assertCanAddApp(packageName.substring(0, packageName.indexOf(":")), true)
                                assertCanAddApp(packageName, false)
                            } else {
                                assertCanAddApp(packageName, true)
                            }
                        }
                    }

                    database.categoryApp().removeCategoryAppsSyncByCategoryIds(
                            packageNames = action.packageNames,
                            categoryIds = allCategoriesOfChild.map { it.id }
                    )

                    // add the apps to the new category
                    database.categoryApp().addCategoryAppsSync(
                            action.packageNames.map {
                                CategoryApp(
                                        categoryId = action.categoryId,
                                        appSpecifierString = it
                                )
                            }
                    )
                }
                is RemoveCategoryAppsAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    // remove the apps from the category
                    database.categoryApp().removeCategoryAppsSyncByCategoryIds(
                            packageNames = action.packageNames,
                            categoryIds = listOf(action.categoryId)
                    )
                }
                is CreateCategoryAction -> {
                    DatabaseValidation.assertChildExists(database, action.childId)

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (action.childId != fromChildSelfLimitAddChildUserId) {
                            throw RuntimeException("can not create categories for other child users")
                        }
                    }

                    // create the category
                    val sort = database.category().getNextCategorySortKeyByChildId(action.childId)

                    database.category().addCategory(Category(
                            id = action.categoryId,
                            childId = action.childId,
                            title = action.title,
                            // nothing blocked by default
                            blockedMinutesInWeek = ImmutableBitmask(BitSet()),
                            extraTimeInMillis = 0,
                            extraTimeDay = -1,
                            temporarilyBlocked = false,
                            temporarilyBlockedEndTime = 0,
                            parentCategoryId = "",
                            blockAllNotifications = false,
                            timeWarnings = 0,
                            minBatteryLevelWhileCharging = 0,
                            minBatteryLevelMobile = 0,
                            sort = sort,
                            disableLimitsUntil = 0,
                            flags = 0L,
                            blockNotificationDelay = 0
                    ))
                }
                is DeleteCategoryAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    // delete all related data and the category
                    database.timeLimitRules().deleteTimeLimitRulesByCategory(action.categoryId)
                    database.usedTimes().deleteUsedTimeItems(action.categoryId)
                    database.categoryApp().deleteCategoryAppsByCategoryId(action.categoryId)
                    database.user().removeAsCategoryForUnassignedApps(action.categoryId)
                    database.category().deleteCategory(action.categoryId)
                }
                is UpdateCategoryTitleAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    database.category().updateCategoryTitle(
                            categoryId = action.categoryId,
                            newTitle = action.newTitle
                    )
                }
                is SetCategoryExtraTimeAction -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    if (action.newExtraTime < 0) {
                        throw IllegalArgumentException("invalid new extra time")
                    }

                    if (action.extraTimeDay < -1) {
                        throw IllegalArgumentException()
                    }

                    database.category().updateCategoryExtraTime(action.categoryId, action.newExtraTime, action.extraTimeDay)
                }
                is IncrementCategoryExtraTimeAction -> {
                    if (action.addedExtraTime < 0) {
                        throw IllegalArgumentException("invalid added extra time")
                    }

                    val category = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category ${action.categoryId} does not exist")

                    fun handleExtratimeIncrement(category: Category) {
                        database.category().updateCategorySync(
                                category.copy(
                                        extraTimeDay = action.extraTimeDay,
                                        extraTimeInMillis = category.getExtraTime(action.extraTimeDay) + action.addedExtraTime
                                )
                        )
                    }

                    handleExtratimeIncrement(category)

                    if (category.parentCategoryId.isNotEmpty()) {
                        val parentCategory = database.category().getCategoryByIdSync(category.parentCategoryId)

                        if (parentCategory?.childId == category.childId) {
                            handleExtratimeIncrement(parentCategory)
                        }
                    }

                    null
                }
                is UpdateCategoryTemporarilyBlockedAction -> {
                    val category = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (category.childId != fromChildSelfLimitAddChildUserId) {
                            throw RuntimeException("can not add limits for different child")
                        }

                        if (action.endTime == null || !action.blocked) {
                            throw RuntimeException("the child may only enable a temporarily blocking")
                        }

                        if (category.temporarilyBlocked) {
                            if (action.endTime < category.temporarilyBlockedEndTime || category.temporarilyBlockedEndTime == 0L) {
                                throw RuntimeException("the child may not reduce the temporarily blocking")
                            }
                        }
                    }

                    database.category().updateCategoryTemporarilyBlocked(
                            categoryId = action.categoryId,
                            blocked = action.blocked,
                            endTime = if (action.blocked) action.endTime ?: 0 else 0
                    )
                }
                is DeleteTimeLimitRuleAction -> {
                    DatabaseValidation.assertTimelimitRuleExists(database, action.ruleId)

                    database.timeLimitRules().deleteTimeLimitRuleByIdSync(action.ruleId)
                }
                is AddUserAction -> {
                    database.user().addUserSync(User(
                            id = action.userId,
                            name = action.name,
                            type = action.userType,
                            timeZone = action.timeZone,
                            password = if (action.password == null) "" else action.password,
                            disableLimitsUntil = 0,
                            categoryForNotAssignedApps = "",
                            flags = 0
                    ))
                }
                is UpdateCategoryBlockedTimesAction -> {
                    val category = database.category().getCategoryByIdSync(action.categoryId) ?: throw IllegalArgumentException("category not found")

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (category.childId != fromChildSelfLimitAddChildUserId) {
                            throw IllegalArgumentException("can not update blocked time areas for other child users")
                        }

                        val oldBlocked = category.blockedMinutesInWeek.dataNotToModify
                        val newBlocked = action.blockedTimes.dataNotToModify

                        var readIndex = 0

                        while (true) {
                            val blockStart = oldBlocked.nextSetBit(readIndex)
                            if (blockStart < 0) break
                            val afterBlockEnd = oldBlocked.nextClearBit(blockStart + 1)

                            if (newBlocked.nextClearBit(blockStart) < afterBlockEnd) {
                                throw IllegalArgumentException("new blocked time areas are smaller")
                            }

                            readIndex = afterBlockEnd + 1
                        }
                    }

                    database.category().updateCategoryBlockedTimes(action.categoryId, action.blockedTimes)
                }
                is CreateTimeLimitRuleAction -> {
                    val category = database.category().getCategoryByIdSync(action.rule.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (fromChildSelfLimitAddChildUserId != category.childId) {
                            throw IllegalArgumentException("can not add rules for other users")
                        }
                    }

                    database.timeLimitRules().addTimeLimitRule(action.rule)
                }
                is UpdateTimeLimitRuleAction -> {
                    val oldRule = database.timeLimitRules().getTimeLimitRuleByIdSync(action.ruleId)!!
                    val updatedRule = oldRule.copy(
                        maximumTimeInMillis = action.maximumTimeInMillis,
                        dayMask = action.dayMask,
                        applyToExtraTimeUsage = action.applyToExtraTimeUsage,
                        startMinuteOfDay = action.start,
                        endMinuteOfDay = action.end,
                        sessionDurationMilliseconds = action.sessionDurationMilliseconds,
                        sessionPauseMilliseconds = action.sessionPauseMilliseconds,
                        perDay = action.perDay,
                        expiresAt = action.expiresAt
                    )

                    if (fromChildSelfLimitAddChildUserId != null) {
                        val category = database.category().getCategoryByIdSync(oldRule.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                        if (fromChildSelfLimitAddChildUserId != category.childId) {
                            throw IllegalArgumentException("can not modify rules of other users")
                        }

                        if (!updatedRule.isAtLeastAsStrictAs(oldRule)) {
                            throw IllegalArgumentException("can not make rule less strict")
                        }
                    }

                    database.timeLimitRules().updateTimeLimitRule(updatedRule)
                }
                is SetDeviceUserAction -> {
                    DatabaseValidation.assertDeviceExists(database, action.deviceId)

                    if (action.userId != "") {
                        DatabaseValidation.assertUserExists(database, action.userId)
                    }

                    database.device().updateDeviceUser(
                            deviceId = action.deviceId,
                            userId = action.userId
                    )
                }
                is SetUserDisableLimitsUntilAction -> {
                    val affectedRows = database.user().updateDisableChildUserLimitsUntil(
                            childId = action.childId,
                            timestamp = action.timestamp
                    )

                    if (affectedRows == 0) {
                        throw IllegalArgumentException("provided user id does not exist")
                    }

                    null
                }
                is UpdateDeviceNameAction -> {
                    val affectedRows = database.device().updateDeviceName(
                            deviceId = action.deviceId,
                            name = action.name
                    )

                    if (affectedRows == 0) {
                        throw IllegalArgumentException("provided device id was invalid")
                    }

                    null
                }
                is RemoveUserAction -> {
                    // authentication is not checked locally, only at the server

                    val userToDelete = database.user().getUserByIdSync(action.userId)!!

                    if (userToDelete.type == UserType.Parent) {
                        val currentParents = database.user().getParentUsersSync()

                        if (currentParents.size <= 1) {
                            throw IllegalStateException("would delete last parent")
                        }

                        if (database.userLimitLoginCategoryDao().countOtherUsersWithoutLimitLoginCategorySync(action.userId) == 0L) {
                            throw IllegalStateException("would delete last user without login limit")
                        }
                    }

                    if (userToDelete.type == UserType.Child) {
                        val categories = database.category().getCategoriesByChildIdSync(userToDelete.id)

                        categories.forEach {
                            category ->

                            dispatchParentActionSync(
                                    action = DeleteCategoryAction(
                                            categoryId = category.id
                                    ),
                                    database = database,
                                    fromChildSelfLimitAddChildUserId = null,
                                    parentUserId = parentUserId
                            )
                        }
                    }

                    database.device().unassignCurrentUserFromAllDevices(action.userId)

                    database.user().deleteUsersByIds(listOf(action.userId))
                }
                is ChangeParentPasswordAction -> {
                    val userEntry = database.user().getUserByIdSync(action.parentUserId)

                    if (userEntry == null || userEntry.type != UserType.Parent) {
                        throw IllegalArgumentException("invalid user entry")
                    }

                    // the client does not have the data to check the integrity

                    database.user().updateUserSync(
                            userEntry.copy(
                                    password = action.newPassword
                            )
                    )
                }
                is IgnoreManipulationAction -> {
                    val originalDeviceEntry = database.device().getDeviceByIdSync(action.deviceId)!!
                    var deviceEntry = originalDeviceEntry

                    if (action.ignoreDeviceAdminManipulation) {
                        deviceEntry = deviceEntry.copy(highestProtectionLevel = deviceEntry.currentProtectionLevel)
                    }

                    if (action.ignoreDeviceAdminManipulationAttempt) {
                        deviceEntry = deviceEntry.copy(manipulationTriedDisablingDeviceAdmin = false)
                    }

                    if (action.ignoreAppDowngrade) {
                        deviceEntry = deviceEntry.copy(highestAppVersion = deviceEntry.currentAppVersion)
                    }

                    if (action.ignoreNotificationAccessManipulation) {
                        deviceEntry = deviceEntry.copy(highestNotificationAccessPermission = deviceEntry.currentNotificationAccessPermission)
                    }

                    if (action.ignoreUsageStatsAccessManipulation) {
                        deviceEntry = deviceEntry.copy(highestUsageStatsPermission = deviceEntry.currentUsageStatsPermission)
                    }

                    if (action.ignoreOverlayPermissionManipulation) {
                        deviceEntry = deviceEntry.copy(highestOverlayPermission = deviceEntry.currentOverlayPermission)
                    }

                    if (action.ignoreAccessibilityServiceManipulation) {
                        deviceEntry = deviceEntry.copy(wasAccessibilityServiceEnabled = deviceEntry.accessibilityServiceEnabled)
                    }

                    if (action.ignoreReboot) {
                        deviceEntry = deviceEntry.copy(manipulationDidReboot = false)
                    }

                    if (action.ignoreHadManipulation) {
                        deviceEntry = deviceEntry.copy(hadManipulation = false)
                    }

                    if (action.ignoreHadManipulationFlags != 0L && deviceEntry.hadManipulationFlags != 0L) {
                        val newFlags = deviceEntry.hadManipulationFlags and (action.ignoreHadManipulationFlags.inv())

                        deviceEntry = deviceEntry.copy(hadManipulationFlags = newFlags)

                        if (newFlags == 0L) {
                            deviceEntry = deviceEntry.copy(hadManipulation = false)
                        }
                    }

                    if (action.ignoreManipulationFlags != 0L) {
                        deviceEntry = deviceEntry.copy(
                            manipulationFlags = deviceEntry.manipulationFlags and (action.ignoreManipulationFlags.inv())
                        )
                    }

                    database.device().updateDeviceEntry(deviceEntry)
                }
                is SetCategoryForUnassignedApps -> {
                    DatabaseValidation.assertChildExists(database, action.childId)

                    if (action.categoryId.isNotEmpty()) {
                        val category = database.category().getCategoryByIdSync(action.categoryId)!!

                        if (category.childId != action.childId) {
                            throw IllegalArgumentException("category does not belong to child")
                        }
                    }

                    database.user().updateCategoryForUnassignedApps(
                            categoryId = action.categoryId,
                            childId = action.childId
                    )
                }
                is SetParentCategory -> {
                    val category = database.category().getCategoryByIdSync(action.categoryId)!!

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (category.childId != fromChildSelfLimitAddChildUserId) {
                            throw RuntimeException("can not set parent category for other user")
                        }
                    }

                    if (action.parentCategory.isNotEmpty()) {
                        val categories = database.category().getCategoriesByChildIdSync(category.childId)

                        categories.find { it.id == action.parentCategory }
                                ?: throw IllegalArgumentException("selected parent category does not exist")

                        val childCategoryIds = categories.getChildCategories(action.categoryId)

                        if (childCategoryIds.contains(action.parentCategory) || action.parentCategory == action.categoryId) {
                            throw IllegalArgumentException("can not set a category as parent which is a child of the category")
                        }

                        if (fromChildSelfLimitAddChildUserId != null) {
                            val ownParentCategory = categories.find { it.id == category.parentCategoryId }
                            val enableDueToLimitAddingWhenChild = ownParentCategory == null || categories.getCategoryWithParentCategories(action.parentCategory).contains(ownParentCategory.id)

                            if (!enableDueToLimitAddingWhenChild) {
                                throw RuntimeException("can not change parent categories in a way which reduces limits")
                            }
                        }
                    }

                    database.category().updateParentCategory(
                            categoryId = action.categoryId,
                            parentCategoryId = action.parentCategory
                    )
                }
                is SetUserTimezoneAction -> {
                    DatabaseValidation.assertUserExists(database, action.userId)

                    database.user().updateUserTimezone(
                            userId = action.userId,
                            timezone = action.timezone
                    )
                }
                is SetChildPasswordAction -> {
                    val userEntry = database.user().getUserByIdSync(action.childId)

                    if (userEntry?.type != UserType.Child) {
                        throw IllegalArgumentException("can not set child password for a child which does not exist")
                    }

                    database.user().updateUserSync(userEntry.copy(password = action.newPasswordHash))
                }
                is SetDeviceDefaultUserAction -> {
                    if (action.defaultUserId.isNotEmpty()) {
                        DatabaseValidation.assertUserExists(database, action.defaultUserId)
                    }

                    DatabaseValidation.assertDeviceExists(database, action.deviceId)

                    database.device().updateDeviceDefaultUser(
                            deviceId = action.deviceId,
                            defaultUserId = action.defaultUserId
                    )
                }
                is SetDeviceDefaultUserTimeoutAction -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("device not found")

                    database.device().updateDeviceEntry(deviceEntry.copy(
                            defaultUserTimeout = action.timeout
                    ))
                }
                is SetConsiderRebootManipulationAction -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("device not found")

                    database.device().updateDeviceEntry(
                            deviceEntry.copy(
                                    considerRebootManipulation = action.considerRebootManipulation
                            )
                    )
                }
                is RenameChildAction -> {
                    val userEntry = database.user().getUserByIdSync(action.childId)

                    if (userEntry?.type != UserType.Child) {
                        throw IllegalArgumentException("can not set child password for a child which does not exist")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    name = action.newName
                            )
                    )
                }
                is UpdateCategoryBlockAllNotificationsAction -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("can not update notification blocking for non exsistent category")

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (fromChildSelfLimitAddChildUserId != categoryEntry.childId) {
                            throw RuntimeException("can not enable filter for other child user")
                        }

                        if (!action.blocked) {
                            throw RuntimeException("can not disable filter as child")
                        }

                        if (categoryEntry.blockAllNotifications && action.blockDelay != null) {
                            throw RuntimeException("can not update the block delay as child")
                        }
                    }

                    database.category().updateCategorySync(
                            categoryEntry.copy(
                                    blockAllNotifications = action.blocked,
                                    blockNotificationDelay = action.blockDelay ?: categoryEntry.blockNotificationDelay
                            )
                    )
                }
                is UpdateEnableActivityLevelBlocking -> {
                    val deviceEntry = database.device().getDeviceByIdSync(action.deviceId)
                            ?: throw IllegalArgumentException("device not found")

                    database.device().updateDeviceEntry(
                            deviceEntry.copy(
                                    enableActivityLevelBlocking = action.enable
                            )
                    )
                }
                is UpdateCategoryTimeWarningsAction -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category not found")

                    val modified = if (action.enable)
                        categoryEntry.copy(
                                timeWarnings = categoryEntry.timeWarnings or action.flags
                        )
                    else
                        categoryEntry.copy(
                                timeWarnings = categoryEntry.timeWarnings and (action.flags.inv())
                        )

                    if (modified != categoryEntry) {
                        database.category().updateCategorySync(modified)
                    }

                    if (action.minutes != null) {
                        if (action.enable) database.timeWarning().insertItemIgnoreConflictSync(
                            CategoryTimeWarning(
                                categoryId = action.categoryId,
                                minutes = action.minutes
                            )
                        )
                        else database.timeWarning().deleteItem(
                            categoryId = action.categoryId,
                            minutes = action.minutes
                        )
                    }

                    null
                }
                is UpdateCategoryBatteryLimit -> {
                    val categoryEntry = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("can not update battery limit for a category which does not exist")

                    database.category().updateCategorySync(
                            categoryEntry.copy(
                                    minBatteryLevelWhileCharging = action.chargingLimit ?: categoryEntry.minBatteryLevelWhileCharging,
                                    minBatteryLevelMobile = action.mobileLimit ?: categoryEntry.minBatteryLevelMobile
                            )
                    )
                }
                is UpdateCategorySortingAction -> {
                    // no validation here:
                    // - only parents can do it
                    // - using it over categories which don't belong together destroys the sorting for both,
                    //   but does not cause any trouble

                    action.categoryIds.forEachIndexed { index, categoryId ->
                        database.category().updateCategorySorting(categoryId, index)
                    }
                }
                is ResetUserKeyAction -> {
                    database.userKey().deleteUserKeySync(action.userId)
                }
                is UpdateUserFlagsAction -> {
                    val user = database.user().getUserByIdSync(action.userId)!!

                    val updatedUser = user.copy(
                            flags = (user.flags and action.modifiedBits.inv()) or action.newValues
                    )

                    database.user().updateUserSync(updatedUser)
                }
                is UpdateUserLimitLoginCategory -> {
                    val user = database.user().getUserByIdSync(action.userId)!!

                    if (user.type != UserType.Parent) {
                        throw IllegalArgumentException()
                    }

                    if (action.categoryId == null) {
                        database.userLimitLoginCategoryDao().removeItemSync(action.userId)
                    } else {
                        if (database.userLimitLoginCategoryDao().countOtherUsersWithoutLimitLoginCategorySync(action.userId) == 0L) {
                            throw IllegalStateException("there must be one user without such limits")
                        }

                        database.category().getCategoryByIdSync(action.categoryId)!!

                        database.userLimitLoginCategoryDao().insertOrReplaceItemSync(
                                UserLimitLoginCategory(
                                        userId = action.userId,
                                        categoryId = action.categoryId,
                                        preBlockDuration = 0
                                )
                        ) }
                }
                is UpdateUserLimitLoginPreBlockDuration -> {
                    val limitUserLogin = database.userLimitLoginCategoryDao().getByParentUserIdSync(action.userId)
                            ?: throw IllegalArgumentException()

                    if (action.preBlockDuration != 0L) {
                        if (action.userId != parentUserId)
                            throw IllegalArgumentException()
                    }

                    database.userLimitLoginCategoryDao().insertOrReplaceItemSync(
                            UserLimitLoginCategory(
                                    userId = action.userId,
                                    categoryId = limitUserLogin.categoryId,
                                    preBlockDuration = action.preBlockDuration
                            )
                    )
                }
                is AddCategoryNetworkId -> {
                    DatabaseValidation.assertCategoryExists(database, action.categoryId)

                    val count = database.categoryNetworkId().countByCategoryIdSync(action.categoryId)

                    if (count + 1 > CategoryNetworkId.MAX_ITEMS) {
                        throw IllegalArgumentException()
                    }

                    val oldItem = database.categoryNetworkId().getByCategoryIdAndItemIdSync(categoryId = action.categoryId, itemId = action.itemId)

                    if (oldItem != null) {
                        throw IllegalArgumentException("id already used")
                    }

                    database.categoryNetworkId().insertItemSync(CategoryNetworkId(
                            categoryId = action.categoryId,
                            networkItemId = action.itemId,
                            hashedNetworkId = action.hashedNetworkId
                    ))
                }
                is ResetCategoryNetworkIds -> {
                    database.categoryNetworkId().deleteByCategoryId(categoryId = action.categoryId)
                }
                is UpdateCategoryDisableLimitsAction -> {
                    val category = database.category().getCategoryByIdSync(action.categoryId)
                            ?: throw IllegalArgumentException("category with the specified id does not exist")

                    if (fromChildSelfLimitAddChildUserId != null) {
                        if (fromChildSelfLimitAddChildUserId != category.childId) {
                            throw RuntimeException("can not modify settings for other child user")
                        }

                        if (action.endTime != 0L) {
                            throw RuntimeException("child user can only disable limitation disabling")
                        }
                    }

                    database.category().updateCategorySync(category.copy(disableLimitsUntil = action.endTime))
                }
                is UpdateChildTaskAction -> {
                    val task = database.childTasks().getTaskByTaskId(taskId = action.taskId)
                    val notFound = task == null

                    if (notFound != action.isNew) {
                        if (action.isNew) {
                            throw IllegalArgumentException("task exists already")
                        } else {
                            throw IllegalArgumentException("task not found")
                        }
                    }

                    if (task == null) {
                        database.childTasks().insertItemSync(
                                ChildTask(
                                        taskId = action.taskId,
                                        taskTitle = action.taskTitle,
                                        categoryId = action.categoryId,
                                        extraTimeDuration = action.extraTimeDuration,
                                        lastGrantTimestamp = 0,
                                        pendingRequest = false
                                )
                        )
                    } else {
                        database.childTasks().updateItemSync(
                                task.copy(
                                        taskTitle = action.taskTitle,
                                        categoryId = action.categoryId,
                                        extraTimeDuration = action.extraTimeDuration,
                                )
                        )
                    }
                }
                is DeleteChildTaskAction -> {
                    val task = database.childTasks().getTaskByTaskId(taskId = action.taskId) ?: throw IllegalArgumentException("task not found")

                    database.childTasks().removeTaskById(taskId = task.taskId)
                }
                is ReviewChildTaskAction -> {
                    val task = database.childTasks().getTaskByTaskId(taskId = action.taskId) ?: throw IllegalArgumentException("task not found")

                    if (!task.pendingRequest) throw IllegalArgumentException("did review of a task which is not pending")

                    if (action.ok) {
                        val category = database.category().getCategoryByIdSync(task.categoryId)!!
                        val resetDayBoundExtraTime = category.extraTimeDay != -1 && action.day != null &&
                                category.extraTimeDay != action.day

                        if (resetDayBoundExtraTime) {
                            database.category().updateCategoryExtraTime(
                                categoryId = category.id,
                                extraTimeDay = -1,
                                newExtraTime = task.extraTimeDuration.toLong()
                            )
                        } else {
                            database.category().updateCategoryExtraTime(
                                categoryId = category.id,
                                extraTimeDay = category.extraTimeDay,
                                newExtraTime = category.extraTimeInMillis + task.extraTimeDuration
                            )
                        }

                        database.childTasks().updateItemSync(task.copy(pendingRequest = false, lastGrantTimestamp = action.time))
                    } else {
                        database.childTasks().updateItemSync(task.copy(pendingRequest = false))
                    }
                }
                is UpdateCategoryFlagsAction -> {
                    val category = database.category().getCategoryByIdSync(action.categoryId)!!

                    database.category().updateCategorySync(
                            category.copy(
                                    flags = (category.flags and action.modifiedBits.inv()) or action.newValues
                            )
                    )
                }
                is AddParentU2FKey -> {
                    if (parentUserId == null) {
                        throw IllegalArgumentException("parent must be authenticated to add key")
                    }

                    database.u2f().addKey(
                        UserU2FKey(
                            keyId = 0,
                            userId = parentUserId,
                            addedAt = System.currentTimeMillis(),
                            keyHandle = action.keyHandle,
                            publicKey = action.publicKey,
                            nextCounter = 0
                        )
                    )
                }
                is RemoveParentU2FKey -> {
                    if (parentUserId == null) {
                        throw IllegalArgumentException("parent must be authenticated to remove key")
                    }

                    database.u2f().deleteKey(
                        parentUserId = parentUserId,
                        keyHandle = action.keyHandle,
                        publicKey = action.publicKey
                    )
                }
            }.let { }
        }
    }
}
