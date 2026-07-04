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
package io.timelimit.android.ui.obsolete

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe

class ObsoleteDialogFragment: DialogFragment() {
    companion object {
        private const val MIN_API_LEVEL = Build.VERSION_CODES.O
        private const val MAX_SKIP_COUNTER = 10

        private const val DIALOG_TAG = "ObsoleteDialogFragment"
        private const val SHARED_PREFS_NAME = "obsolete_os_notification"
        private const val SHARED_PREFS_KEY = "skipped_counter"

        fun show(fragmentActivity: FragmentActivity, force: Boolean) {
            if (Build.VERSION.SDK_INT < MIN_API_LEVEL) {
                if (fragmentActivity.supportFragmentManager.findFragmentByTag(DIALOG_TAG) == null) {
                    val preferences = fragmentActivity.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
                    val counter = preferences.getInt(SHARED_PREFS_KEY, MAX_SKIP_COUNTER)

                    if (force || counter >= MAX_SKIP_COUNTER) {
                        ObsoleteDialogFragment().showSafe(fragmentActivity.supportFragmentManager, DIALOG_TAG)

                        if (counter != 0) {
                            preferences.edit().putInt(SHARED_PREFS_KEY, 0).apply()
                        }
                    } else {
                        preferences.edit().putInt(SHARED_PREFS_KEY, counter + 1).apply()
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = AlertDialog.Builder(context!!, theme)
            .setMessage(R.string.obsolete_message)
            .setPositiveButton(R.string.generic_ok, null)
            .create()
}