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

import android.content.Intent
import android.os.Build
import android.provider.Settings
import io.timelimit.android.R

class EnableBiometricAuthDeniedNoCredentialsDialog :
    ManageBiometricAuthDialog(EnableBiometricAuthDeniedNoCredentialsDialog::class.java.simpleName) {
    override val titleText by lazy { getString(R.string.biometric_manage_no_credentials_dialog_title) }
    override val messageText by lazy { getString(R.string.biometric_manage_no_credentials_dialog_text) }
    override val positiveButtonText by lazy { getString(R.string.biometric_manage_no_credentials_dialog_action) }
    override val negativeButtonText by lazy { getString(R.string.generic_cancel) }

    override fun onPositiveButtonClicked() {
        super.onPositiveButtonClicked()

        @Suppress("DEPRECATION")
        startActivity(
            Intent(
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        Settings.ACTION_BIOMETRIC_ENROLL
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                        Settings.ACTION_FINGERPRINT_ENROLL
                    else ->
                        Settings.ACTION_SECURITY_SETTINGS
                }
            )
        )
    }

    companion object {
        fun newInstance() = EnableBiometricAuthDeniedNoCredentialsDialog()
    }
}
