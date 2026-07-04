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
package io.timelimit.android.ui.contacts

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R

class SelectDialerDialogFragment: DialogFragment() {
    companion object {
        private const val EXTRA_PREPARED_INTENT = "preparedIntent"
        private const val DIALOG_TAG = "SelectDialerDialogFragment"

        fun newInstance(preparedIntent: Intent, contactsFragment: ContactsFragment) = SelectDialerDialogFragment().apply {
            setTargetFragment(contactsFragment, 0)

            arguments = Bundle().apply {
                putParcelable(EXTRA_PREPARED_INTENT, preparedIntent)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val preparedIntent = arguments!!.getParcelable<Intent>(EXTRA_PREPARED_INTENT)!!
        val contactsFragment = targetFragment as ContactsFragment

        val resolveInfo = context!!.packageManager.queryIntentActivities(preparedIntent, 0)

        return AlertDialog.Builder(context!!, theme)
                .setItems(resolveInfo.map { it.activityInfo.loadLabel(context!!.packageManager) }.toTypedArray()) { _, which ->
                    val selection = resolveInfo[which]

                    preparedIntent.setClassName(selection.activityInfo.packageName, selection.activityInfo.name)

                    contactsFragment.startCall(preparedIntent)
                }
                .setNegativeButton(R.string.generic_cancel, null)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, DIALOG_TAG)
}