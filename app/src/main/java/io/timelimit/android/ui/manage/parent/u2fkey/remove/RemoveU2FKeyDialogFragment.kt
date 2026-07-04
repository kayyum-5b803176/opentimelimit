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
package io.timelimit.android.ui.manage.parent.u2fkey.remove

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.RemoveParentU2FKey
import io.timelimit.android.ui.main.getActivityViewModel

class RemoveU2FKeyDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "RemoveU2FKeyDialogFragment"
        private const val USER_ID = "userId"
        private const val PUBLIC_KEY = "publicKey"
        private const val KEY_HANDLE = "keyHandle"

        fun newInstance(userId: String, publicKey: ByteArray, keyHandle: ByteArray) = RemoveU2FKeyDialogFragment().apply {
            arguments = Bundle().apply {
                putString(USER_ID, userId)
                putByteArray(PUBLIC_KEY, publicKey)
                putByteArray(KEY_HANDLE, keyHandle)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val model = getActivityViewModel(requireActivity())
        val userId = requireArguments().getString(USER_ID)!!
        val publicKey = requireArguments().getByteArray(PUBLIC_KEY)!!
        val keyHandle = requireArguments().getByteArray(KEY_HANDLE)!!

        model.authenticatedUser.observe(this) { if (it?.id != userId) dismissAllowingStateLoss() }

        return AlertDialog.Builder(requireContext(), theme)
            .setMessage(R.string.manage_parent_u2f_remove_key_text)
            .setNegativeButton(R.string.generic_no, null)
            .setPositiveButton(R.string.generic_yes) { _, _ ->
                model.tryDispatchParentAction(RemoveParentU2FKey(keyHandle = keyHandle, publicKey = publicKey))
            }
            .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}