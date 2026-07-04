/*
 * Open TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
import io.timelimit.android.databinding.FragmentSetupTermsBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.ui.obsolete.ObsoleteDialogFragment

class SetupTermsFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            ObsoleteDialogFragment.show(requireActivity(), true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupTermsBinding.inflate(inflater, container, false)

        binding.btnAccept.setOnClickListener {
            acceptTerms()
        }

        binding.termsText.movementMethod = LinkMovementMethod.getInstance()

        return binding.root
    }

    private fun acceptTerms() {
        Navigation.findNavController(requireView()).safeNavigate(
                SetupTermsFragmentDirections.actionSetupTermsFragmentToSetupHelpInfoFragment(),
                R.id.setupTermsFragment
        )
    }
}
