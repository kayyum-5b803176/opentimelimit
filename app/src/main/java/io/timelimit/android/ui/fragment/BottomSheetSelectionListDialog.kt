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

package io.timelimit.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.databinding.BottomSheetSelectionListBinding

abstract class BottomSheetSelectionListDialog: BottomSheetDialogFragment() {
    private lateinit var binding: BottomSheetSelectionListBinding
    private var didClearList = false

    abstract val title: String?

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = BottomSheetSelectionListBinding.inflate(inflater, container, false)

        binding.title = title

        return binding.root
    }

    protected fun clearList() { binding.list.removeAllViews(); didClearList = true }

    protected fun addListItem(label: String, checked: Boolean, click: () -> Unit) {
        addListItem(
                buildSingleChoiceRow().also {
                    it.text = label
                    it.isChecked = checked
                    it.setOnClickListener { click() }
                }
        )
    }

    protected fun addListItem(labelRes: Int, checked: Boolean, click: () -> Unit) = addListItem(
            label = getString(labelRes), checked = checked, click = click
    )

    private fun addListItem(view: View) {
        if (!didClearList) throw IllegalStateException()    // this is a source for bugs => require clearing the list first

        binding.list.addView(view)
    }

    private fun buildSingleChoiceRow(): CheckedTextView = LayoutInflater.from(context!!).inflate(
            android.R.layout.simple_list_item_single_choice,
            binding.list,
            false
    ) as CheckedTextView
}