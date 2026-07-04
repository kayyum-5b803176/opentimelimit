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
package io.timelimit.android.ui.help

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.databinding.HelpDialogFragmentBinding
import io.timelimit.android.extensions.showSafe

class HelpDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "HelpDialogFragment"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        fun newInstance(title: Int, text: Int) = HelpDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(EXTRA_TITLE, title)
                putInt(EXTRA_TEXT, text)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = HelpDialogFragmentBinding.inflate(inflater, container, false)

        binding.title = getString(arguments!!.getInt(EXTRA_TITLE))
        binding.text = getString(arguments!!.getInt(EXTRA_TEXT))

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}