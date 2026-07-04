/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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

package io.timelimit.android.ui.manage.child.advanced.duplicate

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.UserType
import io.timelimit.android.sync.actions.*

object DuplicateChildActions {
    suspend fun calculateDuplicateChildActions(userId: String, database: Database, newUserName: String): List<ParentAction> {
        val (data, oldTasks) = Threads.database.executeAndWait {
            database.runInUnobservedTransaction {
                val data = database.derivedDataDao().getUserRelatedDataSync(userId) ?: throw IllegalStateException("user not found")
                val tasks = database.childTasks().getTasksByUserIdSync(userId)

                data to tasks
            }
        }

        return mutableListOf<ParentAction>().also { result ->
            val sourceUser = data.user

            if (sourceUser.type != UserType.Child) throw IllegalArgumentException()

            val newUserId = IdGenerator.generateId()

            result.add(AddUserAction(
                    userId = newUserId,
                    userType = UserType.Child,
                    timeZone = sourceUser.timeZone,
                    name = newUserName,
                    password = null
            ))

            sourceUser.flags.let { flags ->
                if (flags != 0L) result.add(UpdateUserFlagsAction(
                        userId = newUserId,
                        modifiedBits = flags,
                        newValues = flags
                ))
            }

            val newCategoryIds = mutableMapOf<String, String>()

            data.categories.forEach { oldCategory ->
                val newCategoryId = IdGenerator.generateId().also { newCategoryIds[oldCategory.category.id] = it }

                result.add(CreateCategoryAction(
                        childId = newUserId,
                        categoryId = newCategoryId,
                        title = oldCategory.category.title
                ))

                oldCategory.category.blockedMinutesInWeek.let { blockedTimes ->
                    if (!blockedTimes.dataNotToModify.isEmpty) {
                        result.add(UpdateCategoryBlockedTimesAction(
                                categoryId = newCategoryId,
                                blockedTimes = blockedTimes
                        ))
                    }
                }

                if (oldCategory.category.blockAllNotifications) {
                    result.add(UpdateCategoryBlockAllNotificationsAction(
                            categoryId = newCategoryId,
                            blocked = true,
                            blockDelay = oldCategory.category.blockNotificationDelay
                    ))
                }

                oldCategory.category.timeWarnings.let { timeWarnings ->
                    if (timeWarnings != 0) {
                        result.add(UpdateCategoryTimeWarningsAction(
                                categoryId = newCategoryId,
                                enable = true,
                                flags = timeWarnings,
                                minutes = null
                        ))
                    }
                }

                oldCategory.additionalTimeWarnings.forEach { timeWarning ->
                    result.add(UpdateCategoryTimeWarningsAction(
                        categoryId = newCategoryId,
                        enable = true,
                        flags = 0,
                        minutes = timeWarning.minutes
                    ))
                }

                if (oldCategory.category.minBatteryLevelWhileCharging != 0 || oldCategory.category.minBatteryLevelMobile != 0) {
                    result.add(UpdateCategoryBatteryLimit(
                            categoryId = newCategoryId,
                            chargingLimit = oldCategory.category.minBatteryLevelWhileCharging,
                            mobileLimit = oldCategory.category.minBatteryLevelMobile
                    ))
                }

                oldCategory.rules.forEach { oldRule ->
                    result.add(CreateTimeLimitRuleAction(
                            rule = oldRule.copy(id = IdGenerator.generateId(), categoryId = newCategoryId)
                    ))
                }

                oldCategory.networks.forEach { oldNetwork ->
                    result.add(AddCategoryNetworkId(
                            categoryId = newCategoryId,
                            itemId = oldNetwork.networkItemId,
                            hashedNetworkId = oldNetwork.hashedNetworkId
                    ))
                }
            }

            data.categoryApps.groupBy { it.categoryId }.forEach { (oldCategoryId, oldApps) ->
                val newCategoryId = newCategoryIds[oldCategoryId]!!

                result.add(AddCategoryAppsAction(
                        categoryId = newCategoryId,
                        packageNames = oldApps.map { it.appSpecifierString }
                ))
            }

            result.add(UpdateCategorySortingAction(
                    categoryIds = data.sortedCategories().map { newCategoryIds[it.second.category.id]!! }
            ))

            newCategoryIds[sourceUser.categoryForNotAssignedApps]?.let { categoryForNotAssignedApps ->
                result.add(SetCategoryForUnassignedApps(
                        childId = newUserId,
                        categoryId = categoryForNotAssignedApps
                ))
            }

            data.categories.forEach { oldCategory ->
                newCategoryIds[oldCategory.category.parentCategoryId]?.let { newParentCategoryId ->
                    val newCategoryId = newCategoryIds[oldCategory.category.id]!!

                    result.add(SetParentCategory(
                            categoryId = newCategoryId,
                            parentCategory = newParentCategoryId
                    ))
                }
            }

            oldTasks.forEach { oldTask ->
                result.add(UpdateChildTaskAction(
                        isNew = true,
                        taskId = IdGenerator.generateId(),
                        categoryId = newCategoryIds[oldTask.categoryId]!!,
                        taskTitle = oldTask.taskTitle,
                        extraTimeDuration = oldTask.extraTimeDuration
                ))
            }
        }
    }
}