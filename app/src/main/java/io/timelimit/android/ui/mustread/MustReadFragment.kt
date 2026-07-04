/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.ui.mustread

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe

class MustReadFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "MustReadDialog"
        private const val MESSAGE = "message"

        fun newInstance(message: Int) = MustReadFragment().apply {
            arguments = Bundle().apply {
                putInt(MESSAGE, message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val model = ViewModelProviders.of(this).get(MustReadModel::class.java)

        val alert = AlertDialog.Builder(context!!, theme)
                .setMessage(arguments!!.getInt(MESSAGE))
                .setPositiveButton(R.string.generic_ok) { _, _ -> dismiss() }
                .create()

        alert.setOnShowListener {
            val okButton = alert.getButton(AlertDialog.BUTTON_POSITIVE)
            val okString = getString(R.string.generic_ok)

            model.timer.observe(this, Observer {
                okButton.isEnabled = it == 0
                okButton.text = if (it == 0)
                    okString
                else
                    "$okString ($it)"
            })
        }

        return alert
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}