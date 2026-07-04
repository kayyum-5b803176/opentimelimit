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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.logic.DefaultAppLogic

class ChildTaskModel (application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val childIdLive = MutableLiveData<String>()
    private val data = childIdLive.switchMap { childId -> logic.database.childTasks().getTasksByUserIdWithCategoryTitlesLive(userId = childId) }
    private val dataListItemsLive: LiveData<List<ChildTaskItem>> = data.map { items -> items.map { ChildTaskItem.Task(it.childTask, it.categoryTitle) } }
    private val didHideIntroductionLive = logic.database.config().wereHintsShown(HintsToShow.TASKS_INTRODUCTION)
    private var didInit = false

    val listContent = didHideIntroductionLive.switchMap { didHideIntroduction ->
        dataListItemsLive.map { dataListItems ->
            if (didHideIntroduction)
                dataListItems + listOf(ChildTaskItem.Add)
            else
                listOf(ChildTaskItem.Intro) + dataListItems + listOf(ChildTaskItem.Add)
        }
    }

    val isChildTheCurrentDeviceUser = logic.deviceUserId.switchMap { deviceUserId ->
        childIdLive.map { selectedId -> deviceUserId == selectedId }
    }

    fun init(childId: String) {
        if (didInit) return

        didInit = true

        childIdLive.value = childId
    }

    fun hideIntro() {
        Threads.database.submit { logic.database.config().setHintsShownSync(HintsToShow.TASKS_INTRODUCTION) }
    }
}