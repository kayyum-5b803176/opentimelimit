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

package io.timelimit.android.ui.manage.category.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.data.model.Category
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.logic.DefaultAppLogic

class ManageNotificationFilterModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database

    private val childId = MutableLiveData<String>()
    private val categoryId = MutableLiveData<String>()
    private var didInit = false

    var lastBoundEntry: Category? = null

    private val deviceEntry = logic.deviceEntry

    val hasPermission = deviceEntry.map { it?.currentNotificationAccessPermission == NewPermissionStatus.Granted }

    val categoryEntry = childId.switchMap { childId ->
        categoryId.switchMap { categoryId ->
            database.category().getCategoryByChildIdAndId(
                childId = childId,
                categoryId = categoryId
            )
        }
    }

    fun init(categoryId: String, childId: String) {
        if (didInit) return

        didInit = true

        this.childId.value = childId
        this.categoryId.value = categoryId
    }
}