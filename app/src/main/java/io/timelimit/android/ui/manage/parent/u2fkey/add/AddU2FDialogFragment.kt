/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.manage.parent.u2fkey.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.map
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.databinding.AddU2fDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.nfc.NfcStatus
import io.timelimit.android.ui.main.getActivityViewModel

class AddU2FDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AddU2FDialogFragment"
        private const val USER_ID = "userId"

        fun newInstance(userId: String) = AddU2FDialogFragment().apply {
            arguments = Bundle().apply {
                putString(USER_ID, userId)
            }
        }
    }

    private val model: AddU2FModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = AddU2fDialogFragmentBinding.inflate(inflater, container, false)
        val activityModel = getActivityViewModel(requireActivity())
        val userId = requireArguments().getString(USER_ID)!!

        model.init(userId)

        activityModel.authenticatedUser.observe(viewLifecycleOwner) {
            if (it?.id != userId) dismissAllowingStateLoss()
        }

        mergeLiveDataWaitForValues(model.status, U2fManager.with(requireContext()).nfcStatus)
            .map { (status, nfcStatus) ->
                when (status) {
                    AddU2FModel.Status.WaitingForKey -> getString(
                        R.string.manage_parent_u2f_status_wait_key,
                        getString(when (nfcStatus) {
                            NfcStatus.Ready -> R.string.manage_parent_u2f_status_wait_key_nfc_enabled
                            NfcStatus.Disabled -> R.string.manage_parent_u2f_status_wait_key_nfc_disabled
                            NfcStatus.Unsupported -> R.string.manage_parent_u2f_status_wait_key_nfc_unsupported
                        })
                    )
                    AddU2FModel.Status.Working -> getString(R.string.manage_parent_u2f_status_working)
                    AddU2FModel.Status.ConnectionInterrupted -> getString(R.string.manage_parent_u2f_status_interrupted)
                    AddU2FModel.Status.RequestFailed -> getString(R.string.manage_parent_u2f_status_failed)
                    AddU2FModel.Status.AlreadyLinked -> getString(R.string.manage_parent_u2f_status_already_linked)
                    AddU2FModel.Status.NeedsUserInteraction -> getString(R.string.manage_parent_u2f_status_needs_user_interaction)
                    is AddU2FModel.Status.Done -> {
                        if (!status.commited) {
                            activityModel.tryDispatchParentAction(status.action)

                            status.commited = true
                        }

                        getString(R.string.manage_parent_u2f_status_done)
                    }
                }
            }
            .observe(viewLifecycleOwner) { binding.text.text = it }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        U2fManager.with(requireContext()).registerListener(model)
    }

    override fun onPause() {
        super.onPause()

        U2fManager.with(requireContext()).unregisterListener(model)
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}