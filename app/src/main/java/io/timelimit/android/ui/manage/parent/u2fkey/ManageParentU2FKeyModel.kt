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
package io.timelimit.android.ui.manage.parent.u2fkey

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.logic.DefaultAppLogic

class ManageParentU2FKeyModel(application: Application): AndroidViewModel(application) {
    private val userId = MutableLiveData<String>()
    private val database = DefaultAppLogic.with(application).database

    fun init(userId: String) {
        if (this.userId.value != userId) this.userId.value = userId
    }

    private val databaseItems = userId.switchMap { userId -> database.u2f().getByUserLive(userId) }

    val user = userId.switchMap { userId -> database.user().getUserByIdLive(userId) }

    val listItems = databaseItems.map { list ->
        list.map { U2FKeyListItem.KeyItem(it) } + U2FKeyListItem.AddKey
    }
}