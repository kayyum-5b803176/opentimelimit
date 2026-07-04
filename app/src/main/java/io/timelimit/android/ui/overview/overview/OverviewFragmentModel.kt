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
package io.timelimit.android.ui.overview.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.logic.DefaultAppLogic
import java.util.*

class OverviewFragmentModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)

    private val itemVisibility = MutableLiveData<OverviewItemVisibility>().apply { value = OverviewItemVisibility.default }

    private val categoryEntries = logic.database.category().getAllCategoriesShortInfo()
    private val usersWithTemporarilyDisabledLimits = logic.database.user().getAllUsersLive().switchMap {
        users ->

        liveDataFromFunction { logic.timeApi.getCurrentTimeInMillis() }.map {
            currentTime ->

            users.map {
                user ->

                user to (user.disableLimitsUntil >= currentTime)
            }
        }
    }.ignoreUnchanged()
    private val userEntries = usersWithTemporarilyDisabledLimits.switchMap { users ->
        categoryEntries.switchMap { categories ->
            liveDataFromFunction (5000) { logic.timeApi.getCurrentTimeInMillis() }.map { now ->
                users.map { user ->
                    OverviewFragmentItemUser(
                            user = user.first,
                            limitsTemporarilyDisabled = user.second,
                            temporarilyBlocked = categories.find { category ->
                                category.childId == user.first.id &&
                                        category.temporarilyBlocked && (
                                        category.temporarilyBlockedEndTime == 0L ||
                                                category.temporarilyBlockedEndTime > now
                                        )
                            } != null
                    )
                }
            }
        }
    }.ignoreUnchanged()

    private val ownDeviceId = logic.deviceId
    private val devices = logic.database.device().getAllDevicesLive()
    private val devicesWithUsers = devices.switchMap { devices ->
        usersWithTemporarilyDisabledLimits.map { users ->
            devices.map { device ->
                device to users.find { it.first.id == device.currentUserId }
            }
        }
    }
    private val deviceEntries = ownDeviceId.switchMap { thisDeviceId ->
        devicesWithUsers.map { devices ->
            devices.map { (device, user) ->
                OverviewFragmentItemDevice(
                        device = device,
                        deviceUser = user?.first,
                        isCurrentDevice = device.id == thisDeviceId
                )
            }
        }
    }

    private val hasShownIntroduction = logic.database.config().wereHintsShown(HintsToShow.OVERVIEW_INTRODUCTION)
    private val introEntries = hasShownIntroduction.map { hasShownIntro ->
        val result = mutableListOf<OverviewFragmentItem>()

        if (!hasShownIntro) {
            result.add(OverviewFragmentHeaderIntro)
        }

        result
    }

    private val hiddenTaskIdsLive = MutableLiveData<Set<String>>().apply { value = emptySet() }
    private val tasksWithPendingReviewLive = logic.database.childTasks().getPendingTasks()
    private val pendingTasksToShowLive = hiddenTaskIdsLive.switchMap { hiddenTaskIds ->
        tasksWithPendingReviewLive.map { tasksWithPendingReview ->
            tasksWithPendingReview.filterNot { hiddenTaskIds.contains(it.childTask.taskId) }
        }
    }
    private val pendingTaskItemLive = pendingTasksToShowLive.map { tasks ->
        tasks.firstOrNull()?.let {
            TaskReviewOverviewItem(
                task = it.childTask,
                childTitle = it.childName,
                categoryTitle = it.categoryTitle,
                childTimezone = TimeZone.getTimeZone(it.childTimezone)
            )
        }
    }

    fun hideTask(taskId: String) {
        hiddenTaskIdsLive.value = (hiddenTaskIdsLive.value ?: emptySet()) + setOf(taskId)
    }

    val listEntries = introEntries.switchMap { introEntries ->
        deviceEntries.switchMap { deviceEntries ->
            userEntries.switchMap { userEntries ->
                pendingTaskItemLive.switchMap { pendingTaskItem ->
                    itemVisibility.map { itemVisibility ->
                        mutableListOf<OverviewFragmentItem>().apply {
                            addAll(introEntries)

                            if (pendingTaskItem != null) add(pendingTaskItem)

                            add(OverviewFragmentHeaderDevices)
                            addAll(deviceEntries)

                            add(OverviewFragmentHeaderUsers)
                            userEntries.forEach { if (it.user.type != UserType.Parent) add(it) }
                            if (itemVisibility.showParentUsers || userEntries.all { it.user.type == UserType.Parent }) {
                                userEntries.forEach { if (it.user.type == UserType.Parent) add(it) }
                                add(OverviewFragmentActionAddUser)
                            } else {
                                add(ShowMoreOverviewFragmentItem.ShowAllUsers)
                            }
                        }.toList()
                    } as LiveData<List<OverviewFragmentItem>>
                }
            }
        }
    }

    fun showAllUsers() {
        itemVisibility.value = itemVisibility.value!!.copy(showParentUsers = true)
    }
}
