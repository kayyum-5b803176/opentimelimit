/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.ui.manage.parent.password.change

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ChangeParentPasswordAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil

class ChangeParentPasswordViewModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "ChangeParentPassword"
    }

    private val statusInternal = MutableLiveData<ChangeParentPasswordViewModelStatus>().apply {
        value = ChangeParentPasswordViewModelStatus.Idle
    }

    private val logic = DefaultAppLogic.with(application)

    val status = statusInternal.castDown()

    fun confirmError() {
        val value = statusInternal.value

        if (value == ChangeParentPasswordViewModelStatus.Failed || value == ChangeParentPasswordViewModelStatus.WrongPassword) {
            statusInternal.value = ChangeParentPasswordViewModelStatus.Idle
        }
    }

    fun changePassword(parentUserId: String, oldPassword: String, newPassword: String) {
        runAsync {
            try {
                if (statusInternal.value != ChangeParentPasswordViewModelStatus.Idle) {
                    return@runAsync
                }

                statusInternal.value = ChangeParentPasswordViewModelStatus.Working

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "start changePassword()")
                }

                val userEntry = logic.database.user().getUserByIdLive(parentUserId).waitForNullableValue()

                if (userEntry == null || userEntry.type != UserType.Parent) {
                    statusInternal.value = ChangeParentPasswordViewModelStatus.Failed
                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "got userEntry")
                }

                val isOldPasswordCorrect = Threads.crypto.executeAndWait {
                    PasswordHashing.validateSync(oldPassword, userEntry.password)
                }

                if (!isOldPasswordCorrect) {
                    statusInternal.value = ChangeParentPasswordViewModelStatus.WrongPassword
                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "old password is valid")
                }

                val newPasswordHash = PasswordHashing.hashCoroutine(newPassword)

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "created hashs")
                }

                val action = ChangeParentPasswordAction(
                        parentUserId = parentUserId,
                        newPassword = newPasswordHash
                )

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "created action")
                }

                val currentUserEntry = logic.database.user().getUserByIdLive(parentUserId).waitForNullableValue()

                if (currentUserEntry == null || currentUserEntry.password != userEntry.password) {
                    statusInternal.value = ChangeParentPasswordViewModelStatus.Failed
                    return@runAsync
                }

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "validated user a second time")
                }

                ApplyActionUtil.applyParentAction(
                        action = action,
                        database = logic.database,
                        platformIntegration = logic.platformIntegration,
                        fromChildSelfLimitAddChildUserId = null,
                        parentUserId = action.parentUserId
                )

                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "applied action")
                }

                statusInternal.value = ChangeParentPasswordViewModelStatus.Done
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "changing password failed", ex)
                }

                statusInternal.value = ChangeParentPasswordViewModelStatus.Failed
            }
        }
    }
}

enum class ChangeParentPasswordViewModelStatus {
    Idle, Working, Failed, WrongPassword, Done
}
