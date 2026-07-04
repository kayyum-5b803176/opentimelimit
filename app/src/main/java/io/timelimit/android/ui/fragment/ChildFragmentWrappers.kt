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

package io.timelimit.android.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.map
import io.timelimit.android.R
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.category.usagehistory.UsageHistoryFragment
import io.timelimit.android.ui.manage.child.advanced.ManageChildAdvancedFragment
import io.timelimit.android.ui.manage.child.apps.ChildAppsFragment
import io.timelimit.android.ui.manage.child.tasks.ManageChildTasksFragment

abstract class ChildFragmentWrapper: SingleFragmentWrapper() {
    abstract val childId: String
    override val showAuthButton: Boolean = true

    protected val child by lazy { activity.getActivityViewModel().logic.database.user().getChildUserByIdLive(childId) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        child.observe(viewLifecycleOwner) {
            if (it == null) navigation.popBackStack()
        }
    }
}

class ChildAppsFragmentWrapper: ChildFragmentWrapper(), FragmentWithCustomTitle {
    private val params by lazy { ChildAppsFragmentWrapperArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId

    override fun createChildFragment(): Fragment = ChildAppsFragment.newInstance(childId = childId)
    override fun getCustomTitle() = child.map { "${getString(R.string.child_apps_title)} < ${it?.name} < ${getString(R.string.main_tab_overview)}" as String? }
}

class ChildAdvancedFragmentWrapper: ChildFragmentWrapper(), FragmentWithCustomTitle {
    private val params by lazy { ChildAdvancedFragmentWrapperArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId

    override fun createChildFragment(): Fragment = ManageChildAdvancedFragment.newInstance(childId = childId)
    override fun getCustomTitle() = child.map { it?.name }
}

class ChildUsageHistoryFragmentWrapper: ChildFragmentWrapper(), FragmentWithCustomTitle {
    private val params by lazy { ChildUsageHistoryFragmentWrapperArgs.fromBundle(arguments!!) }
    override val childId: String get() = params.childId
    override val showAuthButton: Boolean = false

    override fun createChildFragment(): Fragment = UsageHistoryFragment.newInstance(userId = childId, categoryId = null)
    override fun getCustomTitle() = child.map { it?.let { "${it.name} - ${getString(R.string.usage_history_title)}" } }
}

class ChildTasksFragmentWrapper: ChildFragmentWrapper(), FragmentWithCustomTitle {
    private val params by lazy { ChildTasksFragmentWrapperArgs.fromBundle(requireArguments()) }
    override val childId: String get() = params.childId
    override val showAuthButton: Boolean = true

    override fun createChildFragment(): Fragment = ManageChildTasksFragment.newInstance(childId = childId)
    override fun getCustomTitle() = child.map { it?.let { "${it.name} - ${getString(R.string.manage_child_tasks)}" } }
}