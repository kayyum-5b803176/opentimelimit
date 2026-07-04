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
package io.timelimit.android.ui.launch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.ui.obsolete.ObsoleteDialogFragment
import io.timelimit.android.ui.overview.main.MainFragmentDirections

class LaunchFragment: Fragment() {
    private val model by viewModels<LaunchModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ObsoleteDialogFragment.show(requireActivity(), false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.circular_progress_indicator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navigation = Navigation.findNavController(view)

        model.action.observe(viewLifecycleOwner) {
            when (it) {
                LaunchModel.Action.Setup -> navigation.safeNavigate(LaunchFragmentDirections.actionLaunchFragmentToSetupTermsFragment(), R.id.launchFragment)
                LaunchModel.Action.Overview -> navigation.safeNavigate(LaunchFragmentDirections.actionLaunchFragmentToOverviewFragment(), R.id.launchFragment)
                is LaunchModel.Action.Child -> {
                    navigation.safeNavigate(LaunchFragmentDirections.actionLaunchFragmentToOverviewFragment(), R.id.launchFragment)
                    navigation.safeNavigate(MainFragmentDirections.actionOverviewFragmentToManageChildFragment(it.id, fromRedirect = true), R.id.overviewFragment)
                }
                LaunchModel.Action.ParentMode -> navigation.safeNavigate(LaunchFragmentDirections.actionLaunchFragmentToParentModeFragment(), R.id.launchFragment)
            }
        }
    }
}