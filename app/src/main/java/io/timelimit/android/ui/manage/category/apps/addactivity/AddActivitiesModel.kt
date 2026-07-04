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
package io.timelimit.android.ui.manage.category.apps.addactivity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.logic.DefaultAppLogic

class AddActivitiesModel(application: Application): AndroidViewModel(application) {
    private var didInit = false
    private val paramsLive = MutableLiveData<AddActivitiesParams>()

    fun init(params: AddActivitiesParams) {
        if (didInit) return

        paramsLive.value = params
        didInit = true
    }

    val searchTerm = MutableLiveData<String>().apply { value = "" }

    private val logic = DefaultAppLogic.with(application)

    private val allActivitiesLive = paramsLive.switchMap { params ->
        logic.database.appActivity().getAppActivitiesByPackageName(params.packageName)
    }

    private val userRelatedDataLive = paramsLive.switchMap { params ->
        logic.database.derivedDataDao().getUserRelatedDataLive(params.base.childId)
    }

    private val installedAppsWithCurrentCategories = mergeLiveDataWaitForValues(paramsLive, allActivitiesLive, userRelatedDataLive)
        .map { (params, activities, userRelatedData) ->
            val categoryByAppSpecifier = userRelatedData?.categoryApps?.associateBy { it.appSpecifierString }?.mapValues {
                userRelatedData.categoryById.get(it.value.categoryId)
            } ?: emptyMap()

            activities.map { activity ->
                val specifier = "${params.packageName}:${activity.activityClassName}"
                val category = categoryByAppSpecifier[specifier]?.category

                AddActivityListItem(
                    title = activity.title,
                    className = activity.activityClassName,
                    currentCategoryTitle = category?.title
                )
            }
        }

    private val shownActivities: LiveData<List<AddActivityListItem>> = mergeLiveDataWaitForValues(paramsLive, userRelatedDataLive, installedAppsWithCurrentCategories)
        .map { (params, userRelatedData, allActivities) ->
            if (params.base.isSelfLimitAddingMode) {
                if (userRelatedData == null || !userRelatedData.categoryById.containsKey(params.base.categoryId))
                    emptyList()
                else {
                    val parentCategories = userRelatedData.getCategoryWithParentCategories(params.base.categoryId)
                    val defaultCategory = userRelatedData.categoryById[userRelatedData.user.categoryForNotAssignedApps]

                    val componentToCategoryApp = userRelatedData.categoryApps
                        .filter { it.appSpecifier.packageName == params.packageName }
                        .associateBy { it.appSpecifier.activityName ?: ":" }

                    val baseAppCategoryOrDefaultCategory =
                        userRelatedData.categoryById[componentToCategoryApp[":"]?.categoryId]
                            ?: defaultCategory

                    val isBaseAppInParentCategory = parentCategories.contains(baseAppCategoryOrDefaultCategory?.category?.id)

                    allActivities.filter { activity ->
                        val activityCategoryItem = userRelatedData.categoryById[componentToCategoryApp[activity.className]?.categoryId]
                        val activityItselfInParentCategory = parentCategories.contains(activityCategoryItem?.category?.id)
                        val activityItselfUnassigned = activityCategoryItem == null

                        (isBaseAppInParentCategory && activityItselfUnassigned) || activityItselfInParentCategory
                    }
                }
            } else allActivities
        }

    val filteredActivities = shownActivities.switchMap { activities ->
        searchTerm.map { term ->
            if (term.isEmpty()) {
                activities
            } else {
                activities.filter { it.className.contains(term, ignoreCase = true) or it.title.contains(term, ignoreCase = true) }
            }
        }
    }

    val emptyViewText = allActivitiesLive.switchMap { all ->
        shownActivities.switchMap { shown ->
            filteredActivities.map { filtered ->
                if (filtered.isNotEmpty())
                    EmptyViewText.None
                else if (all.isNotEmpty())
                    if (shown.isEmpty())
                            EmptyViewText.EmptyShown
                    else
                        EmptyViewText.EmptyFiltered
                else /* (all.isEmpty()) */
                    EmptyViewText.EmptyUnfiltered
            }
        }
    }

    enum class EmptyViewText {
        None,
        EmptyShown,
        EmptyFiltered,
        EmptyUnfiltered
    }
}