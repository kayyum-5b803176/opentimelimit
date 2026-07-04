/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
 * Copyright <C> 2020 Marcel Voigt
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
package io.timelimit.android.ui.main

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ParentAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

class ActivityViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ActivityViewModel"
    }

    val logic = DefaultAppLogic.with(application)
    private val database = logic.database

    val shouldHighlightAuthenticationButton = MutableLiveData<Boolean>().apply { value = false }

    private val authenticatedUserMetadata = MutableLiveData<AuthenticatedUser?>().apply { value = null }
    private val deviceUser = logic.deviceUserEntry

    private val authenticatedUserChecked = authenticatedUserMetadata.switchMap { user ->
        val userEntryLive =
            if (user == null) liveDataFromNullableValue(null)
            else database.user().getUserByIdLive(user.userId)

        userEntryLive.map { userEntry ->
            if (userEntry == null) null
            else {
                val valid = when (user) {
                    is AuthenticatedUser.Password -> user.passwordHash == userEntry.password
                    is AuthenticatedUser.LocalAuth -> true
                    null -> false
                }

                if (valid) userEntry
                else null
            }
        }
    }

    private val authenticatedChildChecked: LiveData<User?> = deviceUser.map { user ->
        if (user?.type == UserType.Child && user.allowSelfLimitAdding) {
            user
        } else null
    }

    val authenticatedUserOrChild: LiveData<User?> =
        mergeLiveDataWaitForValues(authenticatedChildChecked, authenticatedUserChecked)
            .map { (localChild, authenticatedUser) ->
                authenticatedUser ?: localChild
            }
            .ignoreUnchanged()

    val authenticatedUser = authenticatedUserOrChild.map { if (it?.type != UserType.Parent) null else it }

    fun isParentAuthenticated(): Boolean {
        val user = authenticatedUserOrChild.value

        return user != null && user.type == UserType.Parent
    }

    fun isParentOrChildAuthenticated(childId: String): Boolean {
        val user = authenticatedUserOrChild.value

        return user != null && (user.type == UserType.Parent || user.id == childId)
    }

    fun requestAuthentication() {
        shouldHighlightAuthenticationButton.value = true
    }

    fun requestAuthenticationOrReturnTrue(): Boolean {
        if (isParentAuthenticated()) {
            return true
        } else {
            requestAuthentication()

            return false
        }
    }

    fun requestAuthenticationOrReturnTrueAllowChild(childId: String): Boolean {
        if (isParentOrChildAuthenticated(childId)) {
            return true
        } else {
            requestAuthentication()

            return false
        }
    }

    fun tryDispatchParentAction(action: ParentAction, allowAsChild: Boolean = false): Boolean = tryDispatchParentActions(listOf(action), allowAsChild)

    fun tryDispatchParentActions(actions: List<ParentAction>, allowAsChild: Boolean = false): Boolean {
        val status = authenticatedUserOrChild.value

        if (status == null || (status.type != UserType.Parent && !allowAsChild)) {
            requestAuthentication()
            return false
        }

        runAsync {
            try {
                actions.forEach { action ->
                    ApplyActionUtil.applyParentAction(
                            action = action,
                            database = database,
                            platformIntegration = logic.platformIntegration,
                            fromChildSelfLimitAddChildUserId = if (allowAsChild && status.type == UserType.Child) status.id else null,
                            parentUserId = if (status.type == UserType.Parent) status.id else null
                    )
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "error dispatching actions", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
            }
        }

        return true
    }

    fun setAuthenticatedUser(user: AuthenticatedUser) {
        authenticatedUserMetadata.value = user
        shouldHighlightAuthenticationButton.value = false
    }

    fun getAuthenticatedUser() = authenticatedUserMetadata.value

    fun logOut() {
        authenticatedUserMetadata.value = null
    }
}

sealed class AuthenticatedUser {
    abstract val userId: String

    data class Password(
        override val userId: String,
        val passwordHash: String
    ): AuthenticatedUser()

    sealed class LocalAuth: AuthenticatedUser() {
        data class U2f(override val userId: String): LocalAuth()
        data class ScanCode(override val userId: String): LocalAuth()
        data class Biometric(override val userId: String): LocalAuth()
    }
}