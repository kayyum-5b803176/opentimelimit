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
package io.timelimit.android.ui.parentmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import io.timelimit.android.R
import io.timelimit.android.databinding.ParentModeFragmentBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.overview.about.AboutFragment

class ParentModeFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DefaultAppLogic.with(requireContext()).platformIntegration.disableDeviceAdmin()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ParentModeFragmentBinding.inflate(inflater, container, false)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.container, ParentModeCodeFragment())
                    .commitNow()
        }

        binding.bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            if (childFragmentManager.isStateSaved) {
                false
            } else {
                childFragmentManager.beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .replace(R.id.container, when (menuItem.itemId) {
                            R.id.parent_mode_tab_code -> ParentModeCodeFragment()
                            R.id.parent_mode_tab_help -> ParentModeHelpFragment()
                            R.id.parent_mode_tab_about -> AboutFragment.newInstance(shownOutsideOfOverview = true)
                            else -> throw IllegalStateException()
                        })
                        .commit()

                true
            }
        }


        return binding.root
    }
}
