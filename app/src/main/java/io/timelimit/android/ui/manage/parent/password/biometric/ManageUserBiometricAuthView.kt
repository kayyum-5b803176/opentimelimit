/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
 * TimeLimit Copyright <C> 2020 Marcel Voigt
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
package io.timelimit.android.ui.manage.parent.password.biometric

import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserFlags
import io.timelimit.android.databinding.ManageUserBiometricAuthViewBinding
import io.timelimit.android.sync.actions.UpdateUserFlagsAction
import io.timelimit.android.ui.extension.bindHelpDialog
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser

object ManageUserBiometricAuthView {
    fun bind(
        view: ManageUserBiometricAuthViewBinding,
        user: LiveData<User?>,
        auth: ActivityViewModel,
        fragmentManager: FragmentManager,
        fragment: Fragment
    ) {
        user.observe(view.lifecycleOwner ?: fragment.viewLifecycleOwner) {
            if (it != null) {
                view.userName = it.name
                view.biometricAuthEnabled = it.biometricAuthEnabled
                view.errorText = when (BiometricManager.from(fragment.requireContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> fragment.getString(R.string.biometric_manage_error_no_hw)
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE, BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                        fragment.getString(R.string.biometric_manage_error_hw_not_available)
                    //BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "" //Handled later by a dialog if necessary
                    //BiometricManager.BIOMETRIC_SUCCESS -> ""
                    else -> ""
                }
            }
        }

        fun toggleUserFlag() {
            user.value?.let { user ->
                auth.tryDispatchParentAction(
                    UpdateUserFlagsAction(
                        userId = user.id,
                        modifiedBits = UserFlags.BIOMETRIC_AUTH_ENABLED,
                        newValues = if (!view.biometricAuthEnabled) UserFlags.BIOMETRIC_AUTH_ENABLED else 0
                    )
                )
            }
        }

        view.toggleBiometricAuthSwitch.setOnCheckedChangeListener { v, isChecked ->
            // Checked state of the switch view shall always reflect the currently active setting:
            // when it's the same there's nothing to do (just updating the UI from user data);
            // when it differs (changed via UI) just reset the UI state, which is updated whenever the user's flag actually has changed.
            if (isChecked == view.biometricAuthEnabled)
                return@setOnCheckedChangeListener
            else
                v.isChecked = view.biometricAuthEnabled

            if (auth.requestAuthenticationOrReturnTrue()) {
                @Suppress("NAME_SHADOWING") val user = user.value ?: return@setOnCheckedChangeListener
                val authenticatedUser = auth.authenticatedUser.value ?: return@setOnCheckedChangeListener

                if (authenticatedUser.id != user.id) {
                    ManageBiometricAuthDeniedNotOwnerDialog.newInstance(userName = user.name).show(fragmentManager)
                } else if (!view.biometricAuthEnabled) {
                    when (BiometricManager.from(fragment.requireContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                            EnableBiometricAuthDeniedNoCredentialsDialog.newInstance().show(fragmentManager)
                        else ->
                            EnableBiometricAuthConfirmDialog.newInstance(userId = user.id, userName = user.name).show(fragmentManager)
                    }
                } else {
                    if (auth.getAuthenticatedUser() is AuthenticatedUser.Password) {
                        toggleUserFlag()
                    } else {
                        DisableBiometricAuthDeniedPasswordRequiredDialog.newInstance().show(fragmentManager)
                    }
                }
            }
        }

        view.titleView.bindHelpDialog(
            titleRes = R.string.biometric_manage_title,
            textRes = R.string.biometric_manage_info,
            fragmentManager = fragmentManager
        )
    }
}
