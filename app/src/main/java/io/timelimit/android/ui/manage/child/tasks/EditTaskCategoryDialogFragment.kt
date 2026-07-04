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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.fragment.BottomSheetSelectionListDialog
import io.timelimit.android.ui.main.getActivityViewModel

class EditTaskCategoryDialogFragment: BottomSheetSelectionListDialog() {
    companion object {
        private const val DIALOG_TAG = "EditTaskCategoryDialogFragment"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(childId: String, categoryId: String?, target: Fragment) = EditTaskCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                if (categoryId != null) putString(CATEGORY_ID, categoryId)
            }

            setTargetFragment(target, 0)
        }
    }

    override val title: String? = null
    private val listener: Listener get() = targetFragment as Listener
    private val auth get() = getActivityViewModel(requireActivity())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = DefaultAppLogic.with(requireContext()).database
        val childId = requireArguments().getString(CHILD_ID)!!
        val currentCategoryId = if (requireArguments().containsKey(CATEGORY_ID)) requireArguments().getString(CATEGORY_ID) else null

        database.user().getChildUserByIdLive(childId).observe(viewLifecycleOwner) {
            if (it == null) dismissAllowingStateLoss()
        }

        auth.authenticatedUser.observe(viewLifecycleOwner) {
            if (it == null) dismissAllowingStateLoss()
        }

        database.category().getCategoriesByChildId(childId).observe(viewLifecycleOwner) { categories ->
            clearList()

            categories.forEach { category ->
                addListItem(
                        label = category.title,
                        checked = category.id == currentCategoryId,
                        click = {
                            listener.onCategorySelected(category.id)

                            dismissAllowingStateLoss()
                        }
                )
            }
        }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)

    interface Listener {
        fun onCategorySelected(categoryId: String)
    }
}