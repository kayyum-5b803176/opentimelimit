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
package io.timelimit.android.ui.manage.child.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.data.model.App
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.view.AppFilterView
import java.util.*

class ChildAppsModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database

    val childIdLive = MutableLiveData<String>()
    val modeLive = MutableLiveData<ChildAppsMode>().apply { value = ChildAppsMode.SortByCategory }
    val appFilterLive = MutableLiveData<AppFilterView.AppFilter>().apply { value = AppFilterView.AppFilter.dummy }

    private val childAppsLive = database.app().getApps()
    private val childCategoriesLive = childIdLive.switchMap { userId -> database.category().getCategoriesByChildId(userId) }
    private val childCategoryIdsLive = childCategoriesLive.map { categories -> categories.map { it.id } }.ignoreUnchanged()
    private val childCategoryAppsLive = childCategoryIdsLive.switchMap { categoryIds -> database.categoryApp().getCategoryApps(categoryIds) }

    val listContentLive = childAppsLive.switchMap { childApps ->
        childCategoriesLive.switchMap { categories ->
            childCategoryAppsLive.switchMap { categoryApps ->
                appFilterLive.ignoreUnchanged().switchMap { appFilter ->
                    val filteredChildApps = childApps.filter { appFilter.matches(it) }
                    val categoryAppByPackageName = categoryApps
                        .filter { it.appSpecifier.activityName == null }
                        .associateBy { it.appSpecifier.packageName }

                    modeLive.ignoreUnchanged().map { mode ->
                        when (mode!!) {
                            ChildAppsMode.SortByCategory -> {
                                val appsByCategoryId = filteredChildApps.groupBy { app ->
                                    categoryAppByPackageName[app.packageName]?.categoryId
                                }

                                val result = mutableListOf<ChildAppsEntry>()

                                fun handleCategory(categoryId: String?, title: String) {
                                    result.add(ChildAppsCategoryHeader(title, categoryId))

                                    val apps = appsByCategoryId[categoryId]

                                    if (apps.isNullOrEmpty()) {
                                        result.add(ChildAppsEmptyCategory(categoryId))
                                    } else {
                                        val sortedApps = apps
                                                .distinctBy { it.packageName }
                                                .sortedBy { it.title.toLowerCase(Locale.getDefault()) }

                                        result.addAll(
                                                sortedApps.map { app ->
                                                    ChildAppsApp(
                                                            app = app,
                                                            // no category name shown when showing category headers
                                                            shownCategoryName = null
                                                    )
                                                }
                                        )
                                    }
                                }

                                categories.forEach { category ->
                                    handleCategory(
                                            categoryId = category.id,
                                            title = category.title
                                    )
                                }

                                handleCategory(
                                        categoryId = null,
                                        title = application.getString(R.string.child_apps_unassigned)
                                )

                                result
                            }
                            ChildAppsMode.SortByTitle -> {
                                val categoryById = categories.associateBy { it.id }

                                filteredChildApps
                                        .distinctBy { it.packageName }
                                        .sortedBy { it.title.toLowerCase(Locale.getDefault()) }
                                        .map { app ->
                                            val categoryId = categoryAppByPackageName[app.packageName]?.categoryId
                                            val category = categoryById[categoryId]

                                            ChildAppsApp(
                                                    app = app,
                                                    shownCategoryName = category?.title
                                            )
                                        }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ChildAppsMode {
    SortByCategory, SortByTitle
}

sealed class ChildAppsEntry
data class ChildAppsCategoryHeader(val title: String, val categoryId: String?): ChildAppsEntry()
data class ChildAppsApp(val app: App, val shownCategoryName: String?): ChildAppsEntry()
data class ChildAppsEmptyCategory(val categoryId: String?): ChildAppsEntry()
