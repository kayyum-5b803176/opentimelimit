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
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.category.blocked_times.BlockedTimeAreasFragment
import io.timelimit.android.ui.manage.category.settings.CategorySettingsFragment

abstract class CategoryFragmentWrapper: SingleFragmentWrapper(), FragmentWithCustomTitle {
    abstract val childId: String
    abstract val categoryId: String
    override val showAuthButton: Boolean = true

    protected val user: LiveData<User?> by lazy {
        activity.getActivityViewModel().logic.database.user().getUserByIdLive(childId)
    }
    protected val category by lazy {
        activity.getActivityViewModel().logic.database.category().getCategoryByChildIdAndId(childId = childId, categoryId = categoryId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        category.observe(viewLifecycleOwner) {
            if (it == null) navigation.popBackStack()
        }
    }

    override fun getCustomTitle(): LiveData<String?> = user.switchMap { user ->
        category.map { category ->
            "${category?.title} < ${user?.name} < ${getString(R.string.main_tab_overview)}" as String?
        }
    }
}

class BlockedTimeAreasFragmentWrapper: CategoryFragmentWrapper() {
    private val params by lazy { BlockedTimeAreasFragmentWrapperArgs.fromBundle(requireArguments()) }
    override val childId: String get() = params.childId
    override val categoryId: String get() = params.categoryId
    override fun createChildFragment(): Fragment = BlockedTimeAreasFragment.newInstance(childId = childId, categoryId = categoryId)
}

class CategoryAdvancedFragmentWrapper: CategoryFragmentWrapper() {
    private val params by lazy { CategoryAdvancedFragmentWrapperArgs.fromBundle(requireArguments()) }
    override val childId: String get() = params.childId
    override val categoryId: String get() = params.categoryId
    override fun createChildFragment(): Fragment = CategorySettingsFragment.newInstance(childId = childId, categoryId = categoryId)
}