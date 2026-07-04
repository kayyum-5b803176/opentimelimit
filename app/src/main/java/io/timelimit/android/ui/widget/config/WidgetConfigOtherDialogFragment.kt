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

class WidgetConfigOtherDialogFragment: DialogFragment() {
    companion object {
        private const val STATE_TRANSLUCENT = "translucent"

        const val DIALOG_TAG = "WidgetConfigOtherDialogFragment"
    }

    private val model: WidgetConfigModel by activityViewModels()
    private var translucent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.state.value?.also {
            if (it is WidgetConfigModel.State.ShowOtherOptions) {
                translucent = it.translucent
            }
        }

        savedInstanceState?.also { translucent = it.getBoolean(STATE_TRANSLUCENT) }

        model.state.observe(this) {
            if (!(it is WidgetConfigModel.State.ShowOtherOptions)) dismissAllowingStateLoss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATE_TRANSLUCENT, translucent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
        .setMultiChoiceItems(
            arrayOf(
                getString(R.string.widget_config_other_translucent)
            ),
            booleanArrayOf(
                translucent
            )
        ) { _, _, checked ->
            translucent = checked
        }
        .setPositiveButton(R.string.wiazrd_next) { _, _ ->
            model.selectOtherOptions(translucent)
        }
        .create()

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        model.userCancel()
    }
}