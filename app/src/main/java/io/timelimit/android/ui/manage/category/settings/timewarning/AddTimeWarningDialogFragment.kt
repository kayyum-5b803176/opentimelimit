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
package io.timelimit.android.ui.manage.category.settings.timewarning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.data.model.CategoryTimeWarning
import io.timelimit.android.data.model.CategoryTimeWarnings
import io.timelimit.android.databinding.AddTimeWarningDialogBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.UpdateCategoryTimeWarningsAction
import io.timelimit.android.ui.main.ActivityViewModelHolder

class AddTimeWarningDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AddTimeWarningDialogFragment"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(categoryId: String) = AddTimeWarningDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = AddTimeWarningDialogBinding.inflate(inflater, container, false)
        val categoryId = requireArguments().getString(CATEGORY_ID)!!
        val auth = (requireActivity() as ActivityViewModelHolder).getActivityViewModel()

        auth.authenticatedUser.observe(viewLifecycleOwner) { if (it == null) dismissAllowingStateLoss() }

        binding.numberPicker.minValue = CategoryTimeWarning.MIN
        binding.numberPicker.maxValue = CategoryTimeWarning.MAX

        binding.confirmButton.setOnClickListener {
            val minutes = binding.numberPicker.value
            val flagIndex = CategoryTimeWarnings.durationInMinutesToBitIndex[minutes]
            val action = if (flagIndex != null) {
                UpdateCategoryTimeWarningsAction(
                    categoryId = categoryId,
                    enable = true,
                    flags = 1 shl flagIndex,
                    minutes = null
                )
            } else {
                UpdateCategoryTimeWarningsAction(
                    categoryId = categoryId,
                    enable = true,
                    flags = 0,
                    minutes = minutes
                )
            }

            if (auth.tryDispatchParentAction(action)) {
                dismissAllowingStateLoss()
            }
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}