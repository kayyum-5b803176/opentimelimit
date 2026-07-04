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

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.databinding.ManageUserBiometricAuthDialogBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.main.getActivityViewModel

interface ManageBiometricAuthDialogHandler {
    fun onPositiveButtonClicked()
    fun onNegativeButtonClicked()
}

abstract class ManageBiometricAuthDialog(private val dialogTag: String) :
    BottomSheetDialogFragment(), ManageBiometricAuthDialogHandler {
    protected abstract val titleText: String
    protected abstract val messageText: String
    protected abstract val positiveButtonText: String
    protected abstract val negativeButtonText: String

    protected val auth by lazy { getActivityViewModel(requireActivity()) }
    protected lateinit var binding: ManageUserBiometricAuthDialogBinding

    var handleCancelAsNegativeButton = true

    override fun onPositiveButtonClicked() = dismiss()
    override fun onNegativeButtonClicked() = dismiss()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ManageUserBiometricAuthDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title = titleText
        binding.text = messageText
        binding.positiveButtonText = positiveButtonText
        binding.negativeButtonText = negativeButtonText
        binding.handler = this
    }

    override fun onCancel(dialog: DialogInterface) {
        if (handleCancelAsNegativeButton) onNegativeButtonClicked()
        super.onCancel(dialog)
    }

    fun show(fragmentManager: FragmentManager) {
        showSafe(fragmentManager, dialogTag)
    }

}
