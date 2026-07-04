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

package io.timelimit.android.ui.manage.child.tasks

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.sync.actions.MarkTaskPendingAction
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.getActivityViewModel

class ConfirmTaskDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "ConfirmTaskDialogFragment"
        private const val TASK_TITLE = "taskTitle"
        private const val TASK_ID = "taskId"
        private const val FROM_MANAGE_SCREEN = "fromManageScreen"

        fun newInstance(taskId: String, taskTitle: String, fromManageScreen: Boolean) = ConfirmTaskDialogFragment().apply {
            arguments = Bundle().apply {
                putString(TASK_ID, taskId)
                putString(TASK_TITLE, taskTitle)
                putBoolean(FROM_MANAGE_SCREEN, fromManageScreen)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val taskId = requireArguments().getString(TASK_ID)!!
        val taskTitle = requireArguments().getString(TASK_TITLE)!!
        val fromManageScreen = requireArguments().getBoolean(FROM_MANAGE_SCREEN)
        val logic = getActivityViewModel(requireActivity()).logic

        val messagePrefix = if (fromManageScreen) getString(R.string.lock_task_confirm_dialog_from_manage_screen) + " " else ""
        val message = messagePrefix + getString(R.string.lock_task_confirm_dialog)

        return AlertDialog.Builder(requireContext(), theme)
                .setTitle(taskTitle)
                .setMessage(message)
                .setNegativeButton(R.string.generic_no, null)
                .setPositiveButton(R.string.generic_yes) { _, _ ->
                    runAsync {
                        ApplyActionUtil.applyAppLogicAction(
                                action = MarkTaskPendingAction(taskId = taskId),
                                appLogic = logic,
                                ignoreIfDeviceIsNotConfigured = true
                        )
                    }
                }
                .create()
    }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, DIALOG_TAG)
}