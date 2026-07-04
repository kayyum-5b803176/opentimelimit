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
package io.timelimit.android.ui.setup

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.SetupHelpInfoFragmentBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.ui.help.HelpDialogFragment

class SetupHelpInfoFragment: Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = SetupHelpInfoFragmentBinding.inflate(inflater, container, false)

        binding.textWithLinks.movementMethod = LinkMovementMethod.getInstance()

        binding.userKeyView.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.manage_user_key_title,
                    text = R.string.manage_user_key_info
            ).show(parentFragmentManager)
        }

        binding.nextButton.setOnClickListener {
            Navigation.findNavController(view!!).safeNavigate(
                    SetupHelpInfoFragmentDirections.actionSetupHelpInfoFragmentToSetupSelectModeFragment(),
                    R.id.setupHelpInfoFragment
            )
        }

        return binding.root
    }
}