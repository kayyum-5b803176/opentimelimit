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
package io.timelimit.android.ui.user.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.map
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentAddUserBinding
import io.timelimit.android.livedata.*
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class AddUserFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        private const val PAGE_INPUT = 0
        private const val PAGE_WAIT = 1
        private const val PAGE_AUTH = 2
    }

    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val model: AddUserModel by lazy { ViewModelProviders.of(this).get(AddUserModel::class.java) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,  savedInstanceState: Bundle?): View? {
        val binding = FragmentAddUserBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)

        // user type

        val userType = MutableLiveData<UserType>()

        fun getUserType(checkedItemId: Int) = when (checkedItemId) {
            R.id.radio_type_child -> UserType.Child
            R.id.radio_type_parent -> UserType.Parent
            else -> throw IllegalStateException()
        }

        userType.value = getUserType(binding.radioGroupType.checkedRadioButtonId)

        binding.radioGroupType.setOnCheckedChangeListener { _, checkedId -> userType.value = getUserType(checkedId) }

        // password

        userType.observe(this, Observer {
            binding.isCreatingChildUser = it == UserType.Child
        })

        val isPasswordOk = binding.password.passwordOk

        binding.password.allowNoPassword.value = true

        // username

        val username = binding.name.getTextLive()
        val usernameOk = username.map { it.isNotBlank() }

        // ok button

        val isEverythingOk = isPasswordOk.and(usernameOk)

        isEverythingOk.observe(this, Observer { binding.createBtn.isEnabled = it!! })

        // create function

        binding.createBtn.setOnClickListener {
            if (auth.isParentAuthenticated()) {
                model.tryCreateUser(
                        name = binding.name.text.toString(),
                        type = userType.value!!,
                        password = binding.password.readPassword(),
                        model = auth
                )
            }
        }


        mergeLiveData(model.status, auth.authenticatedUser).observe(this, Observer {
            data ->

            val (status, user) = data!!

            if (user == null || user.type != UserType.Parent) {
                binding.flipper.displayedChild = PAGE_AUTH
            } else {
                when (status) {
                    AddUserModelStatus.Idle -> {
                        binding.flipper.displayedChild = PAGE_INPUT
                    }
                    AddUserModelStatus.Working -> {
                        binding.flipper.displayedChild = PAGE_WAIT
                    }
                    AddUserModelStatus.Done -> {
                        Snackbar.make(binding.root, R.string.add_user_confirmation_done, Snackbar.LENGTH_SHORT).show()
                        navigation.popBackStack()

                        binding.flipper.displayedChild = PAGE_WAIT
                    }
                    null -> {/* nothing to do */
                    }
                }.let { }
            }
        })

        // link the auth button
        binding.missingAuthView.loginBtn.setOnClickListener { activity.showAuthenticationScreen() }

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.add_user_title)} < ${getString(R.string.main_tab_overview)}")
}
