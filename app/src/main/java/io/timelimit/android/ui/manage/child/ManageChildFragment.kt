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
package io.timelimit.android.ui.manage.child

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.map
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.ui.fragment.ChildFragmentWrapper
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.child.category.ManageChildCategoriesFragment

class ManageChildFragment : ChildFragmentWrapper(), FragmentWithCustomTitle {
    private val params: ManageChildFragmentArgs by lazy { ManageChildFragmentArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId

    override fun createChildFragment(): Fragment = ManageChildCategoriesFragment.newInstance(params)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null && params.fromRedirect) {
            Snackbar.make(binding.coordinator, R.string.manage_child_redirected_toast, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.fragment_manage_child_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_manage_child_apps -> {
            navigation.safeNavigate(
                    ManageChildFragmentDirections.actionManageChildFragmentToChildAppsFragmentWrapper(childId = childId),
                    R.id.manageChildFragment
            )

            true
        }
        R.id.menu_manage_child_advanced -> {
            navigation.safeNavigate(
                    ManageChildFragmentDirections.actionManageChildFragmentToChildAdvancedFragmentWrapper(childId = childId),
                    R.id.manageChildFragment
            )

            true
        }
        R.id.menu_manage_child_phone -> {
            navigation.safeNavigate(
                    ManageChildFragmentDirections.actionManageChildFragmentToContactsFragment(),
                    R.id.manageChildFragment
            )

            true
        }
        R.id.menu_manage_child_usage_history -> {
            navigation.safeNavigate(
                    ManageChildFragmentDirections.actionManageChildFragmentToChildUsageHistoryFragmentWrapper(childId = childId),
                    R.id.manageChildFragment
            )

            true
        }
        R.id.menu_manage_child_tasks -> {
            navigation.safeNavigate(
                    ManageChildFragmentDirections.actionManageChildFragmentToManageChildTasksFragment(childId = childId),
                    R.id.manageChildFragment
            )

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun getCustomTitle() = child.map { "${it?.name} < ${getString(R.string.main_tab_overview)}" as String? }
}
