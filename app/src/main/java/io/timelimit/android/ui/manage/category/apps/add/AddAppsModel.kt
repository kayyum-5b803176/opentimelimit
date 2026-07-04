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
package io.timelimit.android.ui.manage.category.apps.add

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.map
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.derived.CategoryRelatedData
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.DummyApps
import io.timelimit.android.ui.view.AppFilterView
import kotlin.collections.map

class AddAppsModel(application: Application): AndroidViewModel(application) {
    private var didInit = false
    private var paramsLive = MutableLiveData<AddAppsParams>()

    fun init(params: AddAppsParams) {
        if (didInit) return

        paramsLive.value = params
        didInit = true
    }

    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database

    val showAppsFromOtherCategories = MutableLiveData<Boolean>().apply { value = false }
    val filter = MutableLiveData<AppFilterView.AppFilter>().apply { value = AppFilterView.AppFilter.dummy }

    val installedApps = database.app().getApps().map { list ->
        if (list.isEmpty()) list else list + DummyApps.getApps(context = getApplication())
    }

    private val userRelatedDataLive = paramsLive.switchMap { params ->
        database.derivedDataDao().getUserRelatedDataLive(params.childId)
    }

    private val categoryByAppSpecifierLive = userRelatedDataLive.map { data ->
        data?.categoryApps?.associateBy { it.appSpecifierString }?.mapValues {
            data.categoryById.get(it.value.categoryId)
        } ?: emptyMap()
    }

    private val installedAppsWithCurrentCategories = mergeLiveDataWaitForValues(categoryByAppSpecifierLive, installedApps)
        .map { (categoryByAppSpecifier, apps) ->
            apps.map {
                AppWithCategory(it, categoryByAppSpecifier.get(it.packageName))
            }
        }

    private val shownApps = mergeLiveDataWaitForValues(paramsLive, userRelatedDataLive, installedAppsWithCurrentCategories)
        .map { (params, userRelatedData, installedApps) ->
            if (params.isSelfLimitAddingMode) {
                if (userRelatedData == null || !userRelatedData.categoryById.containsKey(params.categoryId))
                    emptyList()
                else {
                    val parentCategories =
                        userRelatedData.getCategoryWithParentCategories(params.categoryId)
                    val defaultCategory =
                        userRelatedData.categoryById[userRelatedData.user.categoryForNotAssignedApps]
                    val allowAppsWithoutCategory =
                        defaultCategory != null && parentCategories.contains(defaultCategory.category.id)
                    val packageNameToCategoryId =
                        userRelatedData.categoryApps
                            .filter { it.appSpecifier.activityName == null }
                            .associateBy { it.appSpecifier.packageName }

                    installedApps.filter { app ->
                        val appCategoryId = packageNameToCategoryId[app.app.packageName]?.categoryId
                        val categoryNotFound = !userRelatedData.categoryById.containsKey(appCategoryId)

                        parentCategories.contains(appCategoryId) || (categoryNotFound && allowAppsWithoutCategory)
                    }
                }
            } else installedApps
        }

    val listItems = filter.switchMap { filter ->
        shownApps.map { filter to it }
    }.map { (search, apps) ->
        apps.filter { search.matches(it.app) }
    }.switchMap { apps ->
        showAppsFromOtherCategories.map { showOtherCategeories ->
            if (showOtherCategeories) apps
            else apps.filter { it.category == null }
        }
    }.map { apps ->
        apps.sortedBy { app -> app.app.title.lowercase() }
    }.map { apps ->
        apps.map { app ->
            AddAppListItem(
                title = app.app.title,
                packageName = app.app.packageName,
                currentCategoryName = app.category?.category?.title
            )
        }
    }

    val emptyViewText: LiveData<EmptyViewText> = listItems.switchMap { items ->
        if (items.isNotEmpty()) {
            // list is not empty ...
            liveDataFromNonNullValue(EmptyViewText.None)
        } else /* items.isEmpty() */ {
            shownApps.map { shownApps ->
                if (shownApps.isNotEmpty()) {
                    EmptyViewText.EmptyDueToFilter
                } else /* if (shownApps.isEmpty()) */ {
                    EmptyViewText.EmptyNoKnownApps
                }
            }
        }
    }

    enum class EmptyViewText {
        None,
        EmptyDueToFilter,
        EmptyNoKnownApps
    }

    internal data class AppWithCategory (val app: App, val category: CategoryRelatedData?)
}