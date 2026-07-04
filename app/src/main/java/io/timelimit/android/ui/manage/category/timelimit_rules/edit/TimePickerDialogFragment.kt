/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.ui.manage.category.timelimit_rules.edit

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import io.timelimit.android.extensions.showSafe

class TimePickerDialogFragment: DialogFragment() {
    companion object {
        private const val START_MINUTE_OF_DAY = "startMinuteOfDay"
        private const val REQUEST_KEY = "requestKey"
        private const val DIALOG_TAG = "TimePickerDialogFragment"

        fun newInstance(
                requestKey: String,
                startMinuteOfDay: Int
        ) = TimePickerDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(START_MINUTE_OF_DAY, startMinuteOfDay)
                putString(REQUEST_KEY, requestKey)
            }
        }
    }

    data class Result (val minuteOfDay: Int) {
        companion object {
            private const val MINUTE_OF_DAY = "minuteOfDay"

            fun fromBundle(bundle: Bundle): Result = Result(minuteOfDay = bundle.getInt(MINUTE_OF_DAY))
        }

        val bundle: Bundle by lazy {
            Bundle().apply { putInt(MINUTE_OF_DAY, minuteOfDay) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val startMinuteOfDay = requireArguments().getInt(START_MINUTE_OF_DAY)
        val requestKey = requireArguments().getString(REQUEST_KEY)!!

        return TimePickerDialog(context, theme, { _, hour, minute ->
            setFragmentResult(requestKey, Result(hour * 60 + minute).bundle)
        }, startMinuteOfDay / 60, startMinuteOfDay % 60, true)
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}