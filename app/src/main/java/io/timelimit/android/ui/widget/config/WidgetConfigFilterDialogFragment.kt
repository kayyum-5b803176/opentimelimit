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
package io.timelimit.android.ui.widget.config

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import io.timelimit.android.R

class WidgetConfigFilterDialogFragment: DialogFragment() {
    companion object {
        private const val STATE_CATEGORY_IDS = "categoryIds"

        const val DIALOG_TAG = "WidgetConfigFilterDialogFragment"
    }

    private val model: WidgetConfigModel by activityViewModels()
    private val selectedCategoryIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.state.value?.also { state ->
            if (state is WidgetConfigModel.State.ShowCategorySelection) {
                selectedCategoryIds.clear()
                selectedCategoryIds.addAll(state.selectedFilterCategories)
            }
        }

        savedInstanceState?.also {
            selectedCategoryIds.clear()
            selectedCategoryIds.addAll(it.getStringArray(STATE_CATEGORY_IDS) ?: emptyArray())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArray(STATE_CATEGORY_IDS, selectedCategoryIds.toTypedArray())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val state = model.state.value

        if (!(state is WidgetConfigModel.State.ShowCategorySelection)) return super.onCreateDialog(savedInstanceState)

        return AlertDialog.Builder(requireContext(), theme)
            .setMultiChoiceItems(
                state.categories.map { it.title }.toTypedArray(),
                state.categories.map { selectedCategoryIds.contains(it.id) }.toBooleanArray()
            ) { _, index, checked ->
                val categoryId = state.categories[index].id

                if (checked) selectedCategoryIds.add(categoryId) else selectedCategoryIds.remove(categoryId)
            }
            .setPositiveButton(R.string.wiazrd_next) { _, _ ->
                if (selectedCategoryIds.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.widget_config_error_filter_empty, Toast.LENGTH_SHORT).show()

                    model.userCancel()
                } else model.selectFilterItems(selectedCategoryIds)
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        model.userCancel()
    }
}