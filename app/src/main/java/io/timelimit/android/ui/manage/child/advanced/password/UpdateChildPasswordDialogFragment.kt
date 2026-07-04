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
package io.timelimit.android.ui.manage.child.advanced.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.databinding.ChangePasswordViewBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic

class UpdateChildPasswordDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val CHILD_ID = "childId"
        private const val DIALOG_TAG = "ucpdf"

        fun newInstance(childId: String) = UpdateChildPasswordDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
            }
        }
    }

    val childId: String by lazy { arguments!!.getString(CHILD_ID)!! }
    val databae: Database by lazy { DefaultAppLogic.with(context!!).database }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        databae.user().getChildUserByIdLive(childId).observe(this, Observer {
            if (it == null) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ChangePasswordViewBinding.inflate(inflater, container, false)
        val model = ViewModelProviders.of(this).get(UpdateChildPasswordViewModel::class.java)

        binding.newPassword.passwordOk.observe(this, Observer {
            binding.canConfirm = it
        })

        binding.saveButton.setOnClickListener {
            model.changePassword(
                    childUserId = childId,
                    newPassword = binding.newPassword.password.value!!,
                    oldPassword = binding.oldPassword.text.toString()
            )
        }

        model.status.observe(this, Observer { status ->
            when (status!!) {
                ChangeChildPasswordViewModelStatus.Idle -> {
                    binding.isWorking = false
                }
                ChangeChildPasswordViewModelStatus.Working -> {
                    binding.isWorking = true
                }
                ChangeChildPasswordViewModelStatus.Failed -> {
                    Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()

                    model.confirmError()
                }
                ChangeChildPasswordViewModelStatus.WrongPassword -> {
                    Toast.makeText(context!!, R.string.manage_parent_change_password_toast_wrong_password, Toast.LENGTH_SHORT).show()

                    model.confirmError()
                }
                ChangeChildPasswordViewModelStatus.Done -> {
                    Toast.makeText(context!!, R.string.manage_parent_change_password_toast_success, Toast.LENGTH_SHORT).show()

                    dismissAllowingStateLoss()

                    null
                }
            }.let {  }
        })

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}