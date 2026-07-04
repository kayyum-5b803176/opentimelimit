/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.login

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.derived.CompleteUserLoginRelatedData
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChildSignInAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import io.timelimit.android.ui.manage.parent.key.ScannedKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LoginDialogFragmentModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "LoginDialogFragment"

        fun formatAllowLoginStatusError(status: AllowUserLoginStatus, context: Context): String = when (status) {
            is AllowUserLoginStatus.Allow -> context.getString(R.string.error_general)
            is AllowUserLoginStatus.ForbidUserNotFound -> context.getString(R.string.error_general)
            is AllowUserLoginStatus.ForbidByCategory -> context.getString(
                    R.string.login_category_blocked,
                    status.categoryTitle,
                    formatBlockingReasonForLimitLoginCategory(status.blockingReason, context)
            )
        }

        fun formatBlockingReasonForLimitLoginCategory(reason: BlockingReason, context: Context) = when (reason) {
            BlockingReason.TemporarilyBlocked -> context.getString(R.string.lock_reason_short_temporarily_blocked)
            BlockingReason.TimeOver -> context.getString(R.string.lock_reason_short_time_over)
            BlockingReason.TimeOverExtraTimeCanBeUsedLater -> context.getString(R.string.lock_reason_short_time_over)
            BlockingReason.BlockedAtThisTime -> context.getString(R.string.lock_reason_short_blocked_time_area)
            BlockingReason.NotificationsAreBlocked -> context.getString(R.string.lock_reason_short_notification_blocking)
            BlockingReason.BatteryLimit -> context.getString(R.string.lock_reason_short_battery_limit)
            BlockingReason.SessionDurationLimit -> context.getString(R.string.lock_reason_short_session_duration)
            BlockingReason.MissingRequiredNetwork -> context.getString(R.string.lock_reason_short_missing_required_network)
            BlockingReason.MissingNetworkCheckPermission -> context.getString(R.string.lock_reason_short_missing_network_check_permission)
            BlockingReason.ForbiddenNetwork -> context.getString(R.string.lock_reason_short_forbidden_network)
            BlockingReason.NotPartOfAnCategory -> "???"
            BlockingReason.None -> "???"
        }
    }

    val selectedUserId = MutableLiveData<String?>().apply { value = null }
    private val logic = DefaultAppLogic.with(application)
    private val users = logic.database.user().getAllUsersLive()
    private val hasParentKeys = logic.database.userKey().countLive().map { it > 0 }
    private val selectedUser = selectedUserId.switchMap { selectedUserId ->
        if (selectedUserId != null)
            logic.database.derivedDataDao().getUserLoginRelatedDataLive(selectedUserId)
        else
            liveDataFromNullableValue(null as CompleteUserLoginRelatedData?)
    }
    private val isCheckingPassword = MutableLiveData<Boolean>().apply { value = false }
    private val wasPasswordWrong = MutableLiveData<Boolean>().apply { value = false }
    var biometricPromptDismissed = false
    private val isLoginDone = MutableLiveData<Boolean>().apply { value = false }
    private val loginLock = Mutex()

    val status: LiveData<LoginDialogStatus> = isLoginDone.switchMap { isLoginDone ->
        if (isLoginDone) {
            liveDataFromNonNullValue(LoginDialogDone as LoginDialogStatus)
        } else {
            selectedUser.switchMap { selectedUserInfo ->
                val selectedUser = selectedUserInfo?.loginRelatedData?.user

                when (selectedUser?.type) {
                    UserType.Parent -> {
                        val loginScreen = isCheckingPassword.switchMap { isCheckingPassword ->
                            wasPasswordWrong.map { wasPasswordWrong ->
                                ParentUserLogin(
                                    isCheckingPassword = isCheckingPassword,
                                    wasPasswordWrong = wasPasswordWrong,
                                    biometricAuthEnabled = selectedUser.biometricAuthEnabled,
                                    userName = selectedUser.name
                                ) as LoginDialogStatus
                            }
                        }

                        AllowUserLoginStatusUtil.calculateLive(logic, selectedUser.id).switchMap { status ->
                            if (status is AllowUserLoginStatus.ForbidByCategory) {
                                liveDataFromNonNullValue(
                                    ParentUserLoginBlockedByCategory(
                                        categoryTitle = status.categoryTitle,
                                        reason = status.blockingReason
                                    ) as LoginDialogStatus
                                )
                            } else {
                                loginScreen
                            }
                        }
                    }
                    UserType.Child -> {
                        if (selectedUser.password.isEmpty()) {
                            liveDataFromNonNullValue(CanNotSignInChildHasNoPassword(childName = selectedUser.name) as LoginDialogStatus)
                        } else {
                            val isAlreadyCurrentUser = selectedUserInfo.deviceRelatedData.deviceEntry.currentUserId == selectedUser.id

                            if (isAlreadyCurrentUser) {
                                liveDataFromNonNullValue(ChildAlreadyDeviceUser as LoginDialogStatus)
                            } else {
                                isCheckingPassword.switchMap { isCheckingPassword ->
                                    wasPasswordWrong.map { wasPasswordWrong ->
                                        ChildUserLogin(
                                                isCheckingPassword = isCheckingPassword,
                                                wasPasswordWrong = wasPasswordWrong
                                        ) as LoginDialogStatus
                                    }
                                }
                            }
                        }
                    }
                    null -> {
                        mergeLiveDataWaitForValues(users, hasParentKeys).map { (users, hasParentKeys) ->
                            UserListLoginDialogStatus(
                                usersToShow = users,
                                showScanOption = hasParentKeys
                            )
                        }
                    }
                }
            }
        }
    }

    fun startSignIn(user: User) {
        biometricPromptDismissed = false
        selectedUserId.value = user.id
    }

    fun tryDefaultLogin(model: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                val allUsers = logic.database.user().getAllUsersLive().waitForNonNullValue()

                allUsers.singleOrNull { it.type == UserType.Parent }?.let { user ->
                    val emptyPasswordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync("", user.password) }

                    val shouldSignIn = if (emptyPasswordValid) {
                        Threads.database.executeAndWait {
                            AllowUserLoginStatusUtil.calculateSync(
                                    logic = logic,
                                    userId = user.id
                            ) is AllowUserLoginStatus.Allow
                        }
                    } else {
                        false
                    }

                    if (shouldSignIn) {
                        model.setAuthenticatedUser(AuthenticatedUser.Password(
                            userId = user.id,
                            passwordHash = user.password
                        ))

                        isLoginDone.value = true
                    }
                }

                if (isLoginDone.value == false && !hasParentKeys.waitForNonNullValue()) {
                    allUsers.singleOrNull { it.password.isNotEmpty() }?.let { user ->
                        selectedUserId.value = user.id
                    }
                }
            }
        }
    }

    fun tryCodeLogin(code: ScannedKey, model: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                val user: User? = Threads.database.executeAndWait {
                    logic.database.runInTransaction {
                        val keyEntry = logic.database.userKey().findUserKeyByPublicKeySync(code.publicKey)

                        if (keyEntry == null) {
                            Threads.mainThreadHandler.post {
                                Toast.makeText(getApplication(), R.string.login_scan_code_err_not_linked, Toast.LENGTH_SHORT).show()
                            }

                            return@runInTransaction null
                        }

                        if (keyEntry.lastUse >= code.timestamp) {
                            Threads.mainThreadHandler.post {
                                Toast.makeText(getApplication(), R.string.login_scan_code_err_expired, Toast.LENGTH_SHORT).show()
                            }

                            return@runInTransaction null
                        }

                        logic.database.userKey().updateKeyTimestamp(code.publicKey, code.timestamp)

                        logic.database.user().getUserByIdSync(keyEntry.userId)
                    }
                }

                if (user != null && user.type == UserType.Parent) {
                    val allowLoginStatus = Threads.database.executeAndWait {
                        AllowUserLoginStatusUtil.calculateSync(
                                logic = logic,
                                userId = user.id
                        )
                    }

                    val shouldSignIn = allowLoginStatus is AllowUserLoginStatus.Allow

                    if (shouldSignIn) {
                        model.setAuthenticatedUser(AuthenticatedUser.LocalAuth.ScanCode(
                            userId = user.id
                        ))

                        isLoginDone.value = true
                    } else {
                        Toast.makeText(getApplication(), formatAllowLoginStatusError(allowLoginStatus, getApplication()), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun tryParentLogin(
            password: String,
            model: ActivityViewModel
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntryInfo = selectedUser.waitForNullableValue()
                    val userEntry = userEntryInfo?.loginRelatedData?.user

                    if (userEntry?.type != UserType.Parent) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    val authenticatedUser = AuthenticatedUser.Password(
                        userId = userEntry.id,
                        passwordHash = userEntry.password
                    )

                    val allowLoginStatus = Threads.database.executeAndWait {
                        AllowUserLoginStatusUtil.calculateSync(
                                logic = logic,
                                userId = userEntry.id
                        )
                    }

                    val shouldSignIn = allowLoginStatus is AllowUserLoginStatus.Allow

                    if (!shouldSignIn) {
                        Toast.makeText(getApplication(), formatAllowLoginStatusError(allowLoginStatus, getApplication()), Toast.LENGTH_SHORT).show()
                        return@runAsync
                    }

                    model.setAuthenticatedUser(authenticatedUser)

                    isLoginDone.value = true
                } finally {
                    isCheckingPassword.value = false
                }
            }
        }
    }

    fun tryChildLogin(
            password: String
    ) {
        runAsync {
            loginLock.withLock {
                try {
                    isCheckingPassword.value = true

                    val userEntryInfo = selectedUser.waitForNullableValue()
                    val userEntry = userEntryInfo?.loginRelatedData?.user

                    if (userEntry?.type != UserType.Child) {
                        selectedUserId.value = null

                        return@runAsync
                    }

                    val passwordValid = Threads.crypto.executeAndWait { PasswordHashing.validateSync(password, userEntry.password) }

                    if (!passwordValid) {
                        wasPasswordWrong.value = true

                        return@runAsync
                    }

                    ApplyActionUtil.applyChildAction(
                            action = ChildSignInAction,
                            database = logic.database,
                            childUserId = userEntry.id
                    )

                    Toast.makeText(getApplication(), R.string.login_child_done_toast, Toast.LENGTH_SHORT).show()

                    isLoginDone.value = true
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "tryChildLogin", ex)
                    }

                    Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()
                } finally {
                    isCheckingPassword.value = false
                }
            }
        }
    }

    fun performBiometricLogin(activityViewModel: ActivityViewModel) {
        runAsync {
            loginLock.withLock {
                selectedUser.waitForNullableValue()?.loginRelatedData?.user?.let { user: User ->
                    if (!user.biometricAuthEnabled) {
                        return@runAsync
                    }

                    val allowLoginStatus = Threads.database.executeAndWait {
                        AllowUserLoginStatusUtil.calculateSync(
                            logic = logic,
                            userId = user.id
                        )
                    }
                    if (allowLoginStatus !is AllowUserLoginStatus.Allow) {
                        Toast.makeText(getApplication(), formatAllowLoginStatusError(allowLoginStatus, getApplication()), Toast.LENGTH_SHORT).show()
                        return@runAsync
                    }

                    activityViewModel.setAuthenticatedUser(AuthenticatedUser.LocalAuth.Biometric(
                        userId = user.id
                    ))
                    isLoginDone.value = true
                }
            }
        }
    }

    fun resetPasswordWrong() {
        if (wasPasswordWrong.value == true) {
            wasPasswordWrong.value = false
        }
    }

    fun goBack(): Boolean {
        return if (status.value is UserListLoginDialogStatus) {
            false
        } else {
            selectedUserId.value = null

            true
        }
    }
}

sealed class LoginDialogStatus
data class UserListLoginDialogStatus(val usersToShow: List<User>, val showScanOption: Boolean): LoginDialogStatus()
data class ParentUserLoginBlockedByCategory(val categoryTitle: String, val reason: BlockingReason): LoginDialogStatus()
data class ParentUserLogin(
    val isCheckingPassword: Boolean,
    val wasPasswordWrong: Boolean,
    val biometricAuthEnabled: Boolean,
    val userName: String
): LoginDialogStatus()
object LoginDialogDone: LoginDialogStatus()
data class CanNotSignInChildHasNoPassword(val childName: String): LoginDialogStatus()
object ChildAlreadyDeviceUser: LoginDialogStatus()
data class ChildUserLogin(
        val isCheckingPassword: Boolean,
        val wasPasswordWrong: Boolean
): LoginDialogStatus()
