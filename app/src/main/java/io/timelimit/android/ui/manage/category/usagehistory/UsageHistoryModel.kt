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
package io.timelimit.android.ui.manage.category.usagehistory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.paging.LivePagedListBuilder
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.logic.DefaultAppLogic

class UsageHistoryModel(application: Application): AndroidViewModel(application) {
    private val database = DefaultAppLogic.with(application).database

    var didInit = false

    val userId = MutableLiveData<String>()
    val categoryId = MutableLiveData<String?>()

    val listContent = userId.switchMap { userId ->
        categoryId.switchMap { categoryId ->
            val items = if (categoryId == null) {
                database.usedTimes().getUsedTimeListItemsByUserId(userId)
            } else {
                database.usedTimes().getUsedTimeListItemsByCategoryId(categoryId)
            }

            LivePagedListBuilder(items, 10).build()
        }
    }

    val selectedCategoryName = userId.switchMap { userId ->
        categoryId.switchMap { categoryId ->
            if (categoryId == null)
                liveDataFromNullableValue(null as String?)
            else
                database.category().getCategoryByChildIdAndId(childId = userId, categoryId = categoryId).map {
                    if (it == null) this.categoryId.value = null

                    it?.title
                }
        }
    }
}