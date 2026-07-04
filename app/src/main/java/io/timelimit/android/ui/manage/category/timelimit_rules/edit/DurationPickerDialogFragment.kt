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
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.DurationPickerDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.view.SelectTimeSpanViewListener

class DurationPickerDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "DurationPickerDialogFragment"
        private const val TITLE_RES = "titleRes"
        private const val REQUEST_KEY = "requestKe"
        private const val START_TIME_IN_MILLIS = "startTimeInMillis"

        fun newInstance(titleRes: Int, requestKey: String, startTimeInMillis: Int) = DurationPickerDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(TITLE_RES, titleRes)
                putString(REQUEST_KEY, requestKey)
                putInt(START_TIME_IN_MILLIS, startTimeInMillis)
            }
        }
    }

    data class Result (val durationInMillis: Int) {
        companion object {
            private const val DURATION_IN_MILLIS = "durationInMillis"

            fun fromBundle(bundle: Bundle) = Result(durationInMillis = bundle.getInt(DURATION_IN_MILLIS))
        }

        val bundle: Bundle by lazy {
            Bundle().apply { putInt(DURATION_IN_MILLIS, durationInMillis) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DurationPickerDialogFragmentBinding.inflate(LayoutInflater.from(requireContext()))
        val view = binding.duration
        val requestKey = requireArguments().getString(REQUEST_KEY)!!
        val titleRes = requireArguments().getInt(TITLE_RES)
        val startTimeInMillis = requireArguments().getInt(START_TIME_IN_MILLIS)
        val config = DefaultAppLogic.with(requireContext()).database.config()

        if (savedInstanceState == null) {
            view.timeInMillis = startTimeInMillis.toLong()
        }

        config.getEnableAlternativeDurationSelectionAsync().observe(this, Observer {
            view.enablePickerMode(it)
        })

        view.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) = Unit

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    config.setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        return AlertDialog.Builder(requireContext(), theme)
                .setTitle(titleRes)
                .setView(binding.root)
                .setPositiveButton(R.string.generic_ok) { _, _ ->
                    setFragmentResult(requestKey, Result(durationInMillis = view.timeInMillis.toInt()).bundle)
                }
                .setNegativeButton(R.string.generic_cancel, null)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}