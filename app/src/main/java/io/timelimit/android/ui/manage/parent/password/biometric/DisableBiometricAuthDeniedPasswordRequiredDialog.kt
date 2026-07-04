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

import io.timelimit.android.R

class DisableBiometricAuthDeniedPasswordRequiredDialog :
    ManageBiometricAuthDialog(DisableBiometricAuthDeniedPasswordRequiredDialog::class.java.simpleName) {
    override val titleText by lazy { getString(R.string.biometric_manage_disable_password_required_dialog_title) }
    override val messageText by lazy { getString(R.string.biometric_manage_disable_password_required_dialog_text) }
    override val positiveButtonText by lazy { getString(R.string.generic_logout) }
    override val negativeButtonText by lazy { getString(R.string.generic_cancel) }

    override fun onPositiveButtonClicked() {
        auth.logOut()
        super.onPositiveButtonClicked()
    }

    companion object {
        fun newInstance() = DisableBiometricAuthDeniedPasswordRequiredDialog()
    }
}
