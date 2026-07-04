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
package io.timelimit.android.ui.manage.category.usagehistory

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.fragment.BottomSheetSelectionListDialog

class SelectUsageHistoryCategoryDialog: BottomSheetSelectionListDialog() {
    companion object {
        private const val USER_ID = "userId"
        private const val CURRENT_CATEGORY_ID = "currentCategoryId"
        private const val DIALOG_TAG = "SelectUsageHistoryCategoryDialog"

        fun newInstance(userId: String, currentCategoryId: String?, target: Fragment) = SelectUsageHistoryCategoryDialog().apply {
            setTargetFragment(target, 0)
            arguments = Bundle().apply {
                putString(USER_ID, userId)
                if (currentCategoryId != null) putString(CURRENT_CATEGORY_ID, currentCategoryId)
            }
        }
    }

    override val title: String? get() = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId: String = requireArguments().getString(USER_ID)!!
        val currentCategoryId: String? = requireArguments().getString(CURRENT_CATEGORY_ID)
        val target = targetFragment as Listener

        DefaultAppLogic.with(requireContext()).database.category().getCategoriesByChildId(userId).observe(viewLifecycleOwner) { categories ->
            clearList()

            categories.forEach {
                addListItem(
                        label = it.title,
                        checked = it.id == currentCategoryId,
                        click = { target.onCategoryFilterSelected(it.id); dismiss() }
                )
            }

            addListItem(
                    labelRes = R.string.usage_history_filter_all_categories,
                    checked = currentCategoryId == null,
                    click = { target.onAllCategoriesSelected(); dismiss() }
            )
        }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)

    interface Listener {
        fun onAllCategoriesSelected()
        fun onCategoryFilterSelected(categoryId: String)
    }
}