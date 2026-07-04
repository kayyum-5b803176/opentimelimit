/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.ui.util

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.EditTextBottomSheetDialogBinding

abstract class EditTextBottomSheetDialog: DialogFragment() {
    private val inputMethodManager: InputMethodManager by lazy {
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    lateinit var binding: EditTextBottomSheetDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext(), theme).apply {
        setOnShowListener {
            Threads.mainThreadHandler.post {
                if (isAdded) {
                    binding.editText.requestFocus()
                    inputMethodManager.showSoftInput(binding.editText, 0)
                }
            }
        }
    }

    fun didInitField() {
        if (isAdded) {
            binding.editText.setSelection(binding.editText.text.length)
            binding.editText.requestFocus()
            inputMethodManager.showSoftInput(binding.editText, 0)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = EditTextBottomSheetDialogBinding.inflate(inflater, container, false)

        binding.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                go()

                true
            } else {
                false
            }
        }
        
        binding.editText.setOnKeyListener { _, keyCode, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                go()

                true
            } else {
                false
            }
        }

        return binding.root
    }

    abstract fun go()
}
