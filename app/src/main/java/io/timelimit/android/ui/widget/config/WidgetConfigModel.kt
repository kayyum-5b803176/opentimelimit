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
package io.timelimit.android.ui.widget.config

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.WidgetCategory
import io.timelimit.android.data.model.WidgetConfig
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.widget.TimesWidgetProvider
import kotlinx.coroutines.launch

class WidgetConfigModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "WidgetConfigModel"
    }

    private val stateInternal = MutableLiveData<State>().apply { value = State.WaitingForInit }
    private val database = DefaultAppLogic.with(application).database

    val state = stateInternal.castDown()

    fun init(appWidgetId: Int) {
        if (state.value != State.WaitingForInit) return
        stateInternal.value = State.Working

        viewModelScope.launch {
            try {
                val (deviceAndUserRelatedData, selectedFilterCategories) = Threads.database.executeAndWait {
                    database.runInTransaction {
                        val deviceAndUserRelatedData = database.derivedDataDao().getUserAndDeviceRelatedDataSync()
                        val selectedFilterCategories = database.widgetCategory().queryByWidgetIdSync(appWidgetId).toSet()

                        Pair(deviceAndUserRelatedData, selectedFilterCategories)
                    }
                }

                val isNoChildUser = deviceAndUserRelatedData?.userRelatedData?.user?.type != UserType.Child
                val currentUserCategories = deviceAndUserRelatedData?.userRelatedData?.sortedCategories()?.map { it.second.category }

                if (currentUserCategories == null || isNoChildUser) {
                    stateInternal.value = State.Unconfigured

                    return@launch
                }

                stateInternal.value = State.ShowModeSelection(appWidgetId, selectedFilterCategories, currentUserCategories)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "selectModeAll", ex)
                }

                stateInternal.value = State.ErrorCancel
            }
        }
    }

    fun selectModeAll() {
        val oldState = state.value
        if (!(oldState is State.ShowModeSelection)) return
        stateInternal.value = State.Working

        viewModelScope.launch {
            try {
                val currentConfig = Threads.database.executeAndWait {
                    database.widgetCategory().deleteByWidgetId(oldState.appWidgetId)

                    database.widgetConfig().queryByWidgetId(oldState.appWidgetId)
                }

                stateInternal.value = State.ShowOtherOptions(
                    appWidgetId = oldState.appWidgetId,
                    translucent = currentConfig?.translucent ?: false
                )
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "selectModeAll", ex)
                }

                stateInternal.value = State.ErrorCancel
            }
        }
    }

    fun selectModeFilter() {
        val oldState = state.value
        if (!(oldState is State.ShowModeSelection)) return
        stateInternal.value = State.Working

        stateInternal.value = State.ShowCategorySelection(
            appWidgetId = oldState.appWidgetId,
            selectedFilterCategories = oldState.selectedFilterCategories,
            categories = oldState.categories
        )
    }

    fun selectFilterItems(selectedCategoryIds: Set<String>) {
        val oldState = state.value
        if (!(oldState is State.ShowCategorySelection)) return
        stateInternal.value = State.Working

        viewModelScope.launch {
            try {
                val currentConfig = Threads.database.executeAndWait {
                    val userAndDeviceRelatedData = database.derivedDataDao().getUserAndDeviceRelatedDataSync()
                    val currentCategoryIds = userAndDeviceRelatedData!!.userRelatedData!!.categoryById.keys

                    val categoriesToRemove = currentCategoryIds - selectedCategoryIds
                    val categoriesToAdd = selectedCategoryIds.filter { currentCategoryIds.contains(it) }

                    if (categoriesToRemove.isNotEmpty()) {
                        database.widgetCategory().deleteByWidgetIdAndCategoryIds(
                            oldState.appWidgetId, categoriesToRemove.toList()
                        )
                    }

                    if (categoriesToAdd.isNotEmpty()) {
                        database.widgetCategory().insert(
                            categoriesToAdd.toList().map { WidgetCategory(oldState.appWidgetId, it) }
                        )
                    }

                    database.widgetConfig().queryByWidgetId(oldState.appWidgetId)
                }

                stateInternal.value = State.ShowOtherOptions(
                    appWidgetId = oldState.appWidgetId,
                    translucent = currentConfig?.translucent ?: false
                )
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "selectModeAll", ex)
                }

                stateInternal.value = State.ErrorCancel
            }
        }
    }

    fun selectOtherOptions(translucent: Boolean) {
        val oldState = state.value
        if (!(oldState is State.ShowOtherOptions)) return
        stateInternal.value = State.Working

        viewModelScope.launch {
            try {
                Threads.database.executeAndWait {
                    database.widgetConfig().upsert(
                        WidgetConfig(
                            widgetId = oldState.appWidgetId,
                            translucent = translucent
                        )
                    )
                }

                TimesWidgetProvider.triggerUpdates(
                    context = getApplication(),
                    appWidgetIds = intArrayOf(oldState.appWidgetId)
                )

                stateInternal.value = State.Done(oldState.appWidgetId)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "selectOtherOptions", ex)
                }

                stateInternal.value = State.ErrorCancel
            }
        }
    }

    fun userCancel() {
        stateInternal.value = State.UserCancel
    }

    sealed class State {
        object WaitingForInit: State()
        object Working: State()
        data class ShowModeSelection(val appWidgetId: Int, val selectedFilterCategories: Set<String>, val categories: List<Category>): State()
        data class ShowCategorySelection(val appWidgetId: Int, val selectedFilterCategories: Set<String>, val categories: List<Category>): State()
        data class ShowOtherOptions(val appWidgetId: Int, val translucent: Boolean): State()
        data class Done(val appWidgetId: Int): State()
        object Unconfigured: State()
        object UserCancel: State()
        object ErrorCancel: State()
    }
}