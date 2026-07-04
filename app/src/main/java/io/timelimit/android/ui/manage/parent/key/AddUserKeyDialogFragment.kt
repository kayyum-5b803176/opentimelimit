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
package io.timelimit.android.ui.manage.parent.key

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.UserKey
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic

class AddUserKeyDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AddUserKeyDialogFragment"
        private const val USER_ID = "userId"

        fun newInstance(userId: String) = AddUserKeyDialogFragment().apply {
            arguments = Bundle().apply {
                putString(USER_ID, userId)
            }
        }
    }

    private val scanBarcode = registerForActivityResult(ScanBarcode()) { result ->
        if (result != null) handleResult(ScannedKey.tryDecode(result))

        dismiss()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
        .setTitle(R.string.manage_user_key_add)
        .setMessage(R.string.manage_user_key_info)
        .setNegativeButton(R.string.generic_cancel, null)
        .setPositiveButton(R.string.generic_go, null)
        .create()
        .also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        scanBarcode.launch(null)
                    } catch (ex: ActivityNotFoundException) {
                        MissingBarcodeScannerDialogFragment.newInstance().show(parentFragmentManager)

                        dismiss()
                    }
                }
            }
        }

    private fun handleResult(key: ScannedKey?) {
        if (key == null) {
            Toast.makeText(requireContext(), R.string.manage_user_key_invalid, Toast.LENGTH_SHORT).show()
        } else {
            val context = requireContext().applicationContext
            val database = DefaultAppLogic.with(context!!).database
            val userId = requireArguments().getString(USER_ID)!!

            Threads.database.execute {
                database.runInTransaction {
                    val old = database.userKey().findUserKeyByPublicKeySync(key.publicKey)

                    if (old == null) {
                        database.userKey().addUserKeySync(
                                UserKey(
                                        userId = userId,
                                        publicKey = key.publicKey,
                                        lastUse = key.timestamp
                                )
                        )

                        Threads.mainThreadHandler.post {
                            Toast.makeText(context, R.string.manage_user_key_added, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Threads.mainThreadHandler.post {
                            Toast.makeText(context, R.string.manage_user_key_other_user, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}