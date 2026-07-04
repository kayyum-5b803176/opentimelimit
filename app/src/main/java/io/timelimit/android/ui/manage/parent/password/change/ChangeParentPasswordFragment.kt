/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.manage.parent.password.change

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.map
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.ChangeParentPasswordFragmentBinding
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class ChangeParentPasswordFragment : Fragment(), FragmentWithCustomTitle {
    val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    val params: ChangeParentPasswordFragmentArgs by lazy {
        ChangeParentPasswordFragmentArgs.fromBundle(arguments!!)
    }
    val parentUser: LiveData<User?> by lazy { logic.database.user().getParentUserByIdLive(params.parentUserId) }

    val model: ChangeParentPasswordViewModel by lazy {
        ViewModelProviders.of(this).get(ChangeParentPasswordViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = ChangeParentPasswordFragmentBinding.inflate(inflater, container, false)

        parentUser.observe(this, Observer {
            parentUser ->

            if (parentUser == null) {
                navigation.popBackStack(R.id.overviewFragment, false)
            }
        })

        binding.newPassword.passwordOk.observe(this, Observer {
            isPasswordOk ->

            binding.canConfirm = isPasswordOk
        })

        binding.saveButton.setOnClickListener {
            model.changePassword(
                    parentUserId = params.parentUserId,
                    oldPassword = binding.oldPassword.text.toString(),
                    newPassword = binding.newPassword.readPassword()
            )
        }

        model.status.observe(this, Observer {
            status ->

            when (status!!) {
                ChangeParentPasswordViewModelStatus.Idle -> {
                    binding.isWorking = false
                }
                ChangeParentPasswordViewModelStatus.Working -> {
                    binding.isWorking = true
                }
                ChangeParentPasswordViewModelStatus.Failed -> {
                    Snackbar.make(
                            binding.saveButton,
                            R.string.error_general,
                            Snackbar.LENGTH_SHORT
                    ).show()

                    model.confirmError()
                }
                ChangeParentPasswordViewModelStatus.WrongPassword -> {
                    Snackbar.make(
                            binding.saveButton,
                            R.string.manage_parent_change_password_toast_wrong_password,
                            Snackbar.LENGTH_SHORT
                    ).show()

                    model.confirmError()
                }
                ChangeParentPasswordViewModelStatus.Done -> {
                    Toast.makeText(context!!, R.string.manage_parent_change_password_toast_success, Toast.LENGTH_SHORT).show()

                    navigation.popBackStack()

                    null
                }
            }.let {  }
        })

        binding.newPassword.allowNoPassword.value = true

        return binding.root
    }

    override fun getCustomTitle() = parentUser.map { "${getString(R.string.manage_parent_change_password_title)} < ${it?.name} < ${getString(R.string.main_tab_overview)}" as String? }
}
