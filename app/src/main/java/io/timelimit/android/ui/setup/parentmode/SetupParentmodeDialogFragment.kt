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
package io.timelimit.android.ui.setup.parentmode

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic

class SetupParentmodeDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "SetupParentmodeDialogFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewModelProvider(this).get(SetupParentmodeDialogModel::class.java)
                .init(DefaultAppLogic.with(context!!).database)
                .observe(this, Observer { ok ->
                    dismissAllowingStateLoss()

                    if (ok) {
                        targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, null)
                    }
                })

        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val result = ProgressDialog(context, theme)

        result.setCanceledOnTouchOutside(false)
        result.setMessage(getString(R.string.setup_select_mode_parent_progress))

        return result
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}