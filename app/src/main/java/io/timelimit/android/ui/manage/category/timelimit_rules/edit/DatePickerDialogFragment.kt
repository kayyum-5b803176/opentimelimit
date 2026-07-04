/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import io.timelimit.android.extensions.showSafe
import org.threeten.bp.LocalDate

class DatePickerDialogFragment: DialogFragment() {
    companion object {
        private const val START_DAY_OF_MONTH = "startDayOfMonth"
        private const val START_MONTH_OF_YEAR = "startMonthOfYear"
        private const val START_YEAR = "atartYear"

        private const val REQUEST_KEY = "requestKey"
        private const val DIALOG_TAG = "DatePickerDialogFragment"

        fun newInstance(
                requestKey: String,
                startDayOfMonth: Int,
                startMonthOfYear: Int,
                startYear: Int
        ) = DatePickerDialogFragment().apply {
            arguments = Bundle().apply {
                putString(REQUEST_KEY, requestKey)
                putInt(START_DAY_OF_MONTH, startDayOfMonth)
                putInt(START_MONTH_OF_YEAR, startMonthOfYear)
                putInt(START_YEAR, startYear)
            }
        }
    }

    data class Result (val year: Int, val month: Int, val day: Int) {
        companion object {
            private const val YEAR = "year"
            private const val MONTH = "month"
            private const val DAY = "day"

            fun fromBundle(bundle: Bundle): Result = Result(
                year = bundle.getInt(YEAR),
                month = bundle.getInt(MONTH),
                day = bundle.getInt(DAY)
            )
        }

        val bundle: Bundle by lazy {
            Bundle().apply {
                putInt(YEAR, year)
                putInt(MONTH, month)
                putInt(DAY, day)
            }
        }

        val localDate: LocalDate by lazy {
            LocalDate.of(year, month, day)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val startDayOfMonth = requireArguments().getInt(START_DAY_OF_MONTH)
        val startMonthOfYear = requireArguments().getInt(START_MONTH_OF_YEAR)
        val startYear = requireArguments().getInt(START_YEAR)
        val requestKey = requireArguments().getString(REQUEST_KEY)!!

        return DatePickerDialog(requireContext(), theme, { _, year, month, day ->
            setFragmentResult(requestKey, Result(year, month + 1, day).bundle)
        }, startYear, startMonthOfYear - 1, startDayOfMonth)
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}