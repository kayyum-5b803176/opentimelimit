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
package io.timelimit.android.ui.overview.main

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.livedata.*
import io.timelimit.android.ui.fragment.SingleFragmentWrapper
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.overview.about.AboutFragmentParentHandlers
import io.timelimit.android.ui.overview.overview.OverviewFragment
import io.timelimit.android.ui.overview.overview.OverviewFragmentParentHandlers

class MainFragment : SingleFragmentWrapper(), OverviewFragmentParentHandlers, AboutFragmentParentHandlers, FragmentWithCustomTitle {
    override val showAuthButton: Boolean = true

    override fun createChildFragment(): Fragment = OverviewFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun openAddUserScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToAddUserFragment(),
                R.id.overviewFragment
        )
    }

    override fun openManageChildScreen(childId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageChildFragment(childId = childId, fromRedirect = false),
                R.id.overviewFragment
        )
    }

    override fun openManageDeviceScreen(deviceId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageDeviceFragment(deviceId),
                R.id.overviewFragment
        )
    }

    override fun openManageParentScreen(parentId: String) {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToManageParentFragment(parentId),
                R.id.overviewFragment
        )
    }

    override fun onShowDiagnoseScreen() {
        navigation.safeNavigate(
                MainFragmentDirections.actionOverviewFragmentToDiagnoseMainFragment(),
                R.id.overviewFragment
        )
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.main_tab_overview)} (${getString(R.string.app_name)})")

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.fragment_main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_main_about -> {
            navigation.safeNavigate(
                    MainFragmentDirections.actionOverviewFragmentToAboutFragmentWrapped(),
                    R.id.overviewFragment
            )

            true
        }
        R.id.menu_main_uninstall -> {
            navigation.safeNavigate(
                    MainFragmentDirections.actionOverviewFragmentToUninstallFragment(),
                    R.id.overviewFragment
            )

            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
