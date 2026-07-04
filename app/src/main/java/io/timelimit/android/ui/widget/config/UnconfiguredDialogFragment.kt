/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.ui.widget.config

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import io.timelimit.android.R

class UnconfiguredDialogFragment: DialogFragment() {
    companion object {
        const val DIALOG_TAG = "UnconfiguredDialogFragment"
    }

    private val model: WidgetConfigModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.state.observe(this) {
            if (!(it is WidgetConfigModel.State.Unconfigured)) dismissAllowingStateLoss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
        .setMessage(R.string.widget_msg_unconfigured)
        .setPositiveButton(R.string.generic_ok) { _, _ -> model.userCancel() }
        .create()

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        model.userCancel()
    }
}