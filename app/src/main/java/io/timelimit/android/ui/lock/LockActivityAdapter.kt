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

package io.timelimit.android.ui.lock

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import io.timelimit.android.R
import kotlin.properties.Delegates

class LockActivityAdapter(fragmentManager: FragmentManager, private val context: Context): FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    var showTasksFragment: Boolean by Delegates.observable(false) { _, _, _ -> notifyDataSetChanged() }

    override fun getCount(): Int = if (showTasksFragment) 3 else 2

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> LockReasonFragment()
        1 -> LockActionFragment()
        2 -> LockTaskFragment()
        else -> throw IllegalArgumentException()
    }

    override fun getPageTitle(position: Int): CharSequence? = context.getString(when (position) {
        0 -> R.string.lock_tab_reason
        1 -> R.string.lock_tab_action
        2 -> R.string.lock_tab_task
        else -> throw IllegalArgumentException()
    })
}