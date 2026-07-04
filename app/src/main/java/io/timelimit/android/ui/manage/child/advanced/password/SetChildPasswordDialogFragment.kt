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
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.SetChildPasswordDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.sync.actions.SetChildPasswordAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel

class SetChildPasswordDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val EXTRA_CHILD_ID = "childId"
        private const val DIALOG_TAG = "SetChildPasswordDialogFragment"

        fun newInstance(childId: String) = SetChildPasswordDialogFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_CHILD_ID, childId)
            }
        }
    }

    val childId: String by lazy { arguments!!.getString(EXTRA_CHILD_ID)!! }
    val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        auth.logic.database.user().getChildUserByIdLive(childId).observe(this, Observer {
            if (it == null) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = SetChildPasswordDialogFragmentBinding.inflate(inflater, container, false)

        binding.passwordView.passwordOk.observe(this, Observer {
            binding.saveButton.isEnabled = it
        })

        binding.saveButton.setOnClickListener {
            val childId = childId
            val password = binding.passwordView.password.value!!
            val auth = auth

            dismissAllowingStateLoss()

            runAsync {
                auth.tryDispatchParentAction(
                        SetChildPasswordAction(
                                childId = childId,
                                newPasswordHash = PasswordHashing.hashCoroutine(password)
                        )
                )
            }
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}
