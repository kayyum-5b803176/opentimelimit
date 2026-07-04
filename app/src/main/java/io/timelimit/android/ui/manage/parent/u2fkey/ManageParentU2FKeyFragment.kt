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
package io.timelimit.android.ui.manage.parent.u2fkey

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.map
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.ManageParentU2fKeyFragmentBinding
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.ui.main.*
import io.timelimit.android.ui.manage.parent.u2fkey.add.AddU2FDialogFragment
import io.timelimit.android.ui.manage.parent.u2fkey.remove.RemoveU2FKeyDialogFragment
import io.timelimit.android.ui.manage.parent.u2fkey.remove.U2FRequiresPasswordForRemovalDialogFragment

class ManageParentU2FKeyFragment : Fragment(), FragmentWithCustomTitle {
    val model: ManageParentU2FKeyModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val navigation = Navigation.findNavController(container!!)
        val params = ManageParentU2FKeyFragmentArgs.fromBundle(requireArguments())
        val binding = ManageParentU2fKeyFragmentBinding.inflate(inflater, container, false)
        val activityModel = getActivityViewModel(requireActivity())
        val adapter = Adapter()

        model.init(params.userId)

        fun isAuthValidOrShowMessage(): Boolean  = if (activityModel.requestAuthenticationOrReturnTrue()) {
            if (activityModel.authenticatedUser.value?.id == params.userId) true
            else {
                U2FWrongUserDialogFragment.newInstance().show(parentFragmentManager)

                false
            }
        } else false

        binding.recycler.also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = adapter
        }

        AuthenticationFab.manageAuthenticationFab(
            fab = binding.fab,
            fragment = this,
            shouldHighlight = activityModel.shouldHighlightAuthenticationButton,
            authenticatedUser = activityModel.authenticatedUser,
            doesSupportAuth = liveDataFromNonNullValue(true)
        )

        binding.fab.setOnClickListener { (requireActivity() as ActivityViewModelHolder).showAuthenticationScreen() }

        adapter.listener = object: Adapter.Handlers {
            override fun onAddKeyClicked() {
                if (isAuthValidOrShowMessage()) {
                    AddU2FDialogFragment.newInstance(userId = params.userId).show(parentFragmentManager)
                }
            }

            override fun onRemoveClicked(keyItem: U2FKeyListItem.KeyItem) {
                if (isAuthValidOrShowMessage()) {
                    if (activityModel.getAuthenticatedUser() is AuthenticatedUser.LocalAuth.U2f) {
                        U2FRequiresPasswordForRemovalDialogFragment.newInstance().show(parentFragmentManager)
                    } else {
                        RemoveU2FKeyDialogFragment.newInstance(
                            userId = params.userId,
                            keyHandle = keyItem.item.keyHandle,
                            publicKey = keyItem.item.publicKey
                        ).show(parentFragmentManager)
                    }
                }
            }
        }

        model.user.observe(viewLifecycleOwner) { if (it == null) navigation.popBackStack(R.id.overviewFragment, false) }

        model.listItems.observe(viewLifecycleOwner) { adapter.items = it }

        return binding.root
    }

    override fun getCustomTitle() = model.user.map { "${getString(R.string.manage_parent_u2f_title)} < ${it?.name} < ${getString(R.string.main_tab_overview)}" as String? }
}