/*
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

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import io.timelimit.android.R
import io.timelimit.android.data.model.UserFlags
import io.timelimit.android.sync.actions.UpdateUserFlagsAction

class EnableBiometricAuthConfirmDialog :
    ManageBiometricAuthDialog(EnableBiometricAuthConfirmDialog::class.java.simpleName) {
    override val titleText by lazy { getString(R.string.biometric_manage_enable_dialog_title) }
    override val messageText by lazy { getString(R.string.biometric_manage_enable_dialog_text, userName) }
    override val positiveButtonText by lazy { getString(R.string.generic_enable) }
    override val negativeButtonText by lazy { getString(R.string.generic_cancel) }

    private lateinit var biometricPrompt: BiometricPrompt
    private val userName by lazy { requireArguments().getString(ARG_USER_NAME) }

    override fun onPositiveButtonClicked() {
        showBiometricPrompt()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        biometricPrompt = BiometricPrompt(requireActivity(), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(
                    context,
                    getString(R.string.biometric_auth_failed, userName) + "\n" +
                            getString(R.string.biometric_auth_failed_reason, errString),
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (setUserFlag()) dismiss()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(
                    context,
                    getString(R.string.biometric_auth_failed, userName),
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun showBiometricPrompt() {
        biometricPrompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_enable_prompt_title))
                .setSubtitle(userName)
                .setDescription(getString(R.string.biometric_enable_prompt_description, userName))
                .setNegativeButtonText(getString(R.string.generic_cancel))
                .setConfirmationRequired(false)
                .build()
        )
    }

    private fun setUserFlag(): Boolean {
        val userId = requireArguments().getString(ARG_USER_ID) ?: return false
        return auth.tryDispatchParentAction(
            UpdateUserFlagsAction(
                userId = userId,
                modifiedBits = UserFlags.BIOMETRIC_AUTH_ENABLED,
                newValues = UserFlags.BIOMETRIC_AUTH_ENABLED
            )
        )
    }

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_USER_NAME = "userName"
        fun newInstance(userId: String, userName: String) = EnableBiometricAuthConfirmDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_USER_ID, userId)
                putString(ARG_USER_NAME, userName)
            }
        }
    }
}
