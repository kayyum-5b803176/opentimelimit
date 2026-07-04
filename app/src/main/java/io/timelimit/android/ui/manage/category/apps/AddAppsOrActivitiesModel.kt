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
package io.timelimit.android.ui.manage.category.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.data.model.UserType
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.manage.category.apps.add.AddAppsParams

class AddAppsOrActivitiesModel(application: Application): AndroidViewModel(application) {
    private var didInit = false
    private var paramsLive = MutableLiveData<AddAppsParams>()

    fun init(params: AddAppsParams) {
        if (didInit) return

        paramsLive.value = params
        didInit = true
    }

    fun isAuthValid(auth: ActivityViewModel) = paramsLive.switchMap { params ->
        auth.authenticatedUserOrChild.map {
            val parentAuthValid = it?.type == UserType.Parent
            val childAuthValid = it?.id == params.childId && params.isSelfLimitAddingMode
            val authValid = parentAuthValid || childAuthValid

            authValid
        }
    }
}