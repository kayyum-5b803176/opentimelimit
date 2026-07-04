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
package io.timelimit.android.ui.manage.child.category

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.UpdateCategoryDisableLimitsAction
import io.timelimit.android.ui.main.getActivityViewModel

class ConfirmEnableLimitsAgainDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "ConfirmEnableLimitsAgainDialogFragment"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(childId: String, categoryId: String) = ConfirmEnableLimitsAgainDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val auth = getActivityViewModel(requireActivity())
        val childId = requireArguments().getString(CHILD_ID)!!
        val categoryId = requireArguments().getString(CATEGORY_ID)!!

        auth.authenticatedUserOrChild.observe(this)
        { if (it?.id != childId) dismissAllowingStateLoss() }

        return AlertDialog.Builder(requireContext(), theme)
            .setTitle(R.string.manage_child_confirm_enable_limits_again_title)
            .setMessage(getString(R.string.manage_child_confirm_enable_limits_again_text, ""))
            .setNegativeButton(R.string.generic_cancel, null)
            .setPositiveButton(R.string.generic_enable) { _, _ ->
                auth.tryDispatchParentAction(
                    action = UpdateCategoryDisableLimitsAction(
                        categoryId = categoryId,
                        endTime = 0
                    ),
                    allowAsChild = true
                )
            }
            .create()
            .also { alert ->
                auth.logic.database.category().getCategoryByChildIdAndId(childId, categoryId).observe(this)
                { category ->
                    if (category == null) dismissAllowingStateLoss()
                    else alert.setMessage(getString(
                        R.string.manage_child_confirm_enable_limits_again_text,
                        category.title
                    ))
                }
            }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}