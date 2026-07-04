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
package io.timelimit.android.ui.manage.category.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryTitleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.util.EditTextBottomSheetDialog

class RenameCategoryDialogFragment: EditTextBottomSheetDialog() {
    companion object {
        private const val TAG = "RenameCategoryDialogFragment"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(childId: String, categoryId: String) = RenameCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val childId: String get() = requireArguments().getString(CHILD_ID)!!
    private val categoryId: String get() = requireArguments().getString(CATEGORY_ID)!!

    val categoryEntry: LiveData<Category?> by lazy {
        appLogic.database.category().getCategoryByChildIdAndId(
                categoryId = categoryId,
                childId = childId
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        categoryEntry.observe(this, Observer {
            if (it == null) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title = getString(R.string.category_settings_rename)

        if (savedInstanceState == null) {
            runAsync {
                categoryEntry.waitForNullableValue()?.let { entry ->
                    binding.editText.setText(entry.title)
                    didInitField()
                }
            }
        }
    }

    override fun go() {
        val newTitle = binding.editText.text.toString()

        if (newTitle.isBlank()) {
            Toast.makeText(requireContext(), R.string.category_settings_rename_empty, Toast.LENGTH_SHORT).show()
        } else {
            auth.tryDispatchParentAction(
                    UpdateCategoryTitleAction(
                            categoryId = categoryId,
                            newTitle = newTitle
                    )
            )
        }

        dismiss()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}
