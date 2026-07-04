/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.SingleFragmentWrapperBinding
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab

abstract class SingleFragmentWrapper: Fragment() {
    val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private lateinit var navController: NavController
    protected lateinit var binding: SingleFragmentWrapperBinding

    protected val navigation get() = navController

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        navController = Navigation.findNavController(container!!)

        binding = SingleFragmentWrapperBinding.inflate(inflater, container, false)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                fragment = this,
                shouldHighlight = activity.getActivityViewModel().shouldHighlightAuthenticationButton,
                authenticatedUser = activity.getActivityViewModel().authenticatedUser,
                doesSupportAuth = liveDataFromNonNullValue(showAuthButton)
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.container, createChildFragment())
                    .commit()
        }

        return binding.root
    }

    abstract fun createChildFragment(): Fragment
    abstract val showAuthButton: Boolean
}