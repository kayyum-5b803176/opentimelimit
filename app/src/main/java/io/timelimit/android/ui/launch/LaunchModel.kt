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
package io.timelimit.android.ui.launch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitUntilValueMatches
import io.timelimit.android.logic.DefaultAppLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaunchModel(application: Application): AndroidViewModel(application) {
    private val actionInternal = MutableLiveData<Action>()
    private val logic = DefaultAppLogic.with(application)

    val action = actionInternal.castDown()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                logic.isInitialized.waitUntilValueMatches { it == true }

                actionInternal.value = Threads.database.executeAndWait {
                    val hasDeviceId = logic.database.config().getOwnDeviceIdSync() != null
                    val hasParentKey = logic.database.config().getParentModeKeySync() != null

                    if (hasDeviceId) {
                        val config = logic.database.derivedDataDao().getUserAndDeviceRelatedDataSync()

                        if (config?.userRelatedData?.user?.type == UserType.Child) Action.Child(config.userRelatedData.user.id)
                        else Action.Overview
                    }
                    else if (hasParentKey) Action.ParentMode
                    else Action.Setup
                }
            }
        }
    }

    sealed class Action {
        object Setup: Action()
        object Overview: Action()
        data class Child(val id: String): Action()
        object ParentMode: Action()
    }
}