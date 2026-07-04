/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.manage.child.category.create

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.CreateCategoryAction
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.util.EditTextBottomSheetDialog


class CreateCategoryDialogFragment: EditTextBottomSheetDialog() {
    companion object {
        private const val DIALOG_TAG = "CreateCategoryDialogFragment"
        private const val CHILD_ID = "childId"

        fun newInstance(childId: String) = CreateCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
            }
        }
    }

    private val childId: String
        get() = arguments!!.getString(CHILD_ID)!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title = getString(R.string.create_category_title)
        binding.editText.hint = getString(R.string.create_category_hint)
    }

    override fun go() {
        val text = binding.editText.text.toString()
        val auth = getActivityViewModel(activity!!)

        if (text.isNotEmpty()) {
            auth.tryDispatchParentAction(
                    action = CreateCategoryAction(
                            categoryId = IdGenerator.generateId(),
                            title = text,
                            childId = childId
                    ),
                    allowAsChild = true
            )
        }

        dismiss()
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }
}
