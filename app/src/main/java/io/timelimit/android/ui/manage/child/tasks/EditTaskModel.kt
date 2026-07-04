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

package io.timelimit.android.ui.manage.child.tasks

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.DeleteChildTaskAction
import io.timelimit.android.sync.actions.UpdateChildTaskAction
import io.timelimit.android.ui.main.ActivityViewModel
import java.lang.IllegalArgumentException

class EditTaskModel(application: Application): AndroidViewModel(application) {
    val logic = DefaultAppLogic.with(application)

    private var didInit = false
    private var originalTask: ChildTask? = null
    private val childIdLive = MutableLiveData<String>()
    private val taskIdLive = MutableLiveData<String?>()
    private val isBusyInternal = MutableLiveData<Boolean>().apply { value = true }
    private val shouldCloseInternal = MutableLiveData<Boolean>().apply { value = false }
    private val isMissingTask = taskIdLive.switchMap { taskId ->
        if (taskId == null) liveDataFromNonNullValue(false)
        else logic.database.childTasks().getTaskByTaskIdLive(taskId).map { it == null}
    }
    val categoryIdLive = MutableLiveData<String?>()
    val taskTitleLive = MutableLiveData<String>()
    val durationLive = MutableLiveData<Long>()
    val isBusy = isBusyInternal.or(isMissingTask)
    val shouldClose = shouldCloseInternal.castDown()

    private val selectedCategory = childIdLive.switchMap { childId ->
        categoryIdLive.switchMap { categoryId ->
            if (categoryId != null)
                logic.database.category().getCategoryByChildIdAndId(childId = childId, categoryId = categoryId)
            else
                liveDataFromNullableValue(null)
        }
    }

    val selectedCategoryTitle = selectedCategory.map { it?.title }

    private val validCategory = selectedCategory.map { it != null }
    private val validTitle = taskTitleLive.map { it.isNotBlank() && it.length <= ChildTask.MAX_TASK_TITLE_LENGTH }
    private val durationValid = durationLive.map { it > 0 && it <= ChildTask.MAX_EXTRA_TIME }
    val valid = validCategory.and(validTitle).and(durationValid)

    fun init(childId: String, taskId: String?) {
        if (didInit) return; didInit = true

        childIdLive.value = childId
        taskIdLive.value = taskId
        categoryIdLive.value = null
        taskTitleLive.value = ""
        durationLive.value = 1000L * 60 * 15

        runAsync {
            if (taskId != null) {
                val task = logic.database.childTasks().getTaskByTaskIdCoroutine(taskId)

                if (task != null) {
                    categoryIdLive.value = task.categoryId
                    taskTitleLive.value = task.taskTitle
                    durationLive.value = task.extraTimeDuration.toLong()

                    originalTask = task
                } else {
                    shouldCloseInternal.value = true
                }
            }

            isBusyInternal.value = false
        }
    }

    fun deleteRule(auth: ActivityViewModel, onTaskRemoved: (ChildTask) -> Unit) {
        val taskId = taskIdLive.value
        val oldTask = originalTask

        if (taskId != null && oldTask != null) {
            isBusyInternal.value = true

            auth.tryDispatchParentAction(
                    DeleteChildTaskAction(taskId = taskId)
            )

            onTaskRemoved(oldTask)

            shouldCloseInternal.value = true
        }
    }

    fun saveRule(auth: ActivityViewModel) {
        isBusyInternal.value = true

        val taskId = taskIdLive.value
        val categoryId = categoryIdLive.value ?: return
        val duration = durationLive.value ?: return
        val taskTitle = taskTitleLive.value ?: return

        try {
            if (taskId == null) {
                auth.tryDispatchParentAction(
                        UpdateChildTaskAction(
                                taskId = IdGenerator.generateId(),
                                categoryId = categoryId,
                                extraTimeDuration = duration.toInt(),
                                isNew = true,
                                taskTitle = taskTitle
                        )
                )
            } else {
                auth.tryDispatchParentAction(
                        UpdateChildTaskAction(
                                taskId = taskId,
                                categoryId = categoryId,
                                extraTimeDuration = duration.toInt(),
                                isNew = false,
                                taskTitle = taskTitle
                        )
                )
            }
        } catch (ex: IllegalArgumentException) {
            Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
        }

        shouldCloseInternal.value = true
    }
}