/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

package io.timelimit.android.ui.manage.child.advanced.duplicate

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.main.getActivityViewModel

class DuplicateChildDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "DuplicateChildDialogFragment"
        private const val CHILD_ID = "childId"

        fun newInstance(childId: String) = DuplicateChildDialogFragment().apply {
            arguments = Bundle().apply { putString(CHILD_ID, childId) }
        }
    }

    private val model: DuplicateChildModel by viewModels()
    private val childId: String get() = requireArguments().getString(CHILD_ID)!!
    private val auth get() = getActivityViewModel(requireActivity())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.logic.database.user().getChildUserByIdLive(childId).observe(this) { if (it == null) dismissAllowingStateLoss() }
        auth.authenticatedUser.observe(this) { if (it == null) dismissAllowingStateLoss() }

        model.status.observe(this) { status ->
            when (status) {
                is DuplicateChildModel.Status.WaitingForConfirmation -> {/* do nothing */}
                is DuplicateChildModel.Status.Preparing -> {/* do nothing */}
                is DuplicateChildModel.Status.HasAction -> {
                    Toast.makeText(requireContext(), R.string.duplicate_child_done_toast, Toast.LENGTH_SHORT).show()

                    auth.tryDispatchParentActions(status.actions)

                    dismissAllowingStateLoss()
                }
                is DuplicateChildModel.Status.Failure -> {
                    Toast.makeText(requireContext(), R.string.error_general, Toast.LENGTH_SHORT).show()

                    dismissAllowingStateLoss()
                }
            }.let {/* require handling all paths */}
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
            .setMessage(R.string.duplicate_child_message)
            .setNegativeButton(R.string.generic_no, null)
            .setPositiveButton(R.string.generic_yes, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val yesButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    val noButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                    yesButton.setOnClickListener { model.start(childId) }

                    model.status.observe(this) {
                        val enableButtons = it is DuplicateChildModel.Status.WaitingForConfirmation

                        isCancelable = enableButtons
                        noButton.isEnabled = enableButtons
                        yesButton.isEnabled = enableButtons
                    }
                }
            }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}