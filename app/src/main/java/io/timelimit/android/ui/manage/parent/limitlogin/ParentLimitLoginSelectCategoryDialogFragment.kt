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

package io.timelimit.android.ui.manage.parent.limitlogin

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.UpdateUserLimitLoginCategory
import io.timelimit.android.ui.fragment.BottomSheetSelectionListDialog
import io.timelimit.android.ui.main.ActivityViewModelHolder

class ParentLimitLoginSelectCategoryDialogFragment: BottomSheetSelectionListDialog() {
    companion object {
        private const val DIALOG_TAG = "ParentLimitLoginSelectCategoryDialogFragment"
        private const val USER_ID = "userId"

        fun newInstance(userId: String) = ParentLimitLoginSelectCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(USER_ID, userId)
            }
        }
    }

    override val title: String get() = getString(R.string.parent_limit_login_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = arguments!!.getString(USER_ID)!!
        val auth = (activity as ActivityViewModelHolder).getActivityViewModel()
        val logic = auth.logic
        val options = logic.database.userLimitLoginCategoryDao().getLimitLoginCategoryOptions(userId)

        options.switchMap { a ->
            auth.authenticatedUser.map { b ->
                a to b
            }
        }.observe(viewLifecycleOwner, Observer { (categoryList, user) ->
            if (user?.type != UserType.Parent) {
                dismissAllowingStateLoss(); return@Observer
            }

            val isUserItself = user.id == userId

            val hasSelection = categoryList.find { it.selected } != null

            clearList()

            categoryList.forEach { category ->
                addListItem(
                        label = getString(R.string.parent_limit_login_dialog_item, category.childTitle, category.categoryTitle),
                        checked = category.selected,
                        click = {
                            if (!category.selected) {
                                if (isUserItself) {
                                    auth.tryDispatchParentAction(
                                            UpdateUserLimitLoginCategory(
                                                    userId = userId,
                                                    categoryId = category.categoryId
                                            )
                                    )

                                    dismiss()
                                } else {
                                    LimitLoginRestrictedToUserItselfDialogFragment().show(parentFragmentManager)
                                }
                            } else {
                                dismiss()
                            }
                        }
                )
            }

            addListItem(
                    labelRes = R.string.parent_limit_login_dialog_no_selection,
                    checked = !hasSelection,
                    click = {
                        if (hasSelection) {
                            auth.tryDispatchParentAction(
                                    UpdateUserLimitLoginCategory(
                                            userId = userId,
                                            categoryId = null
                                    )
                            )
                        }

                        dismiss()
                    }
            )
        })
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}