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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe

class MissingBarcodeScannerDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "MissingBarcodeScannerDialogFragment"

        fun newInstance() = MissingBarcodeScannerDialogFragment()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext(), theme)
        .setTitle(R.string.scan_key_missing_title)
        .setMessage(R.string.scan_key_missing_text)
        .setNegativeButton(R.string.generic_cancel, null)
        .setPositiveButton(R.string.scan_key_missing_install) { _, _ ->
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=de.markusfisch.android.binaryeye")

                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.error_general, Toast.LENGTH_SHORT).show()
            }
        }
        .create()

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}