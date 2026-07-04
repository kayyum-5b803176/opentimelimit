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
package io.timelimit.android.ui.manage.category.blocked_times


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.CopyBlockedTimeAreasDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder

class CopyBlockedTimeAreasDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val TAG = "cbtadf"
        private const val SELECTED_START_DAY = "ssd"

        fun newInstance(target: Fragment) = CopyBlockedTimeAreasDialogFragment().apply {
            setTargetFragment(target, 0)
        }
    }

    var selectedStartDayIndex = -1
    val auth: ActivityViewModel by lazy {
        (activity as ActivityViewModelHolder).getActivityViewModel()
    }
    val target: CopyBlockedTimeAreasDialogFragmentListener by lazy {
        targetFragment as CopyBlockedTimeAreasDialogFragmentListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            selectedStartDayIndex = savedInstanceState.getInt(SELECTED_START_DAY)
        }

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(SELECTED_START_DAY, selectedStartDayIndex)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = CopyBlockedTimeAreasDialogFragmentBinding.inflate(inflater, container, false)
        val dayNames = resources.getStringArray(R.array.days_of_week_array)

        val dayCheckboxes = listOf(
                binding.toMonday,
                binding.toTuesday,
                binding.toWednesday,
                binding.toThursday,
                binding.toFriday,
                binding.toSaturday,
                binding.toSunday
        )

        fun bindSecondPage() {
            dayCheckboxes.forEach { it.isEnabled = true }

            if (selectedStartDayIndex != -1) {
                dayCheckboxes[selectedStartDayIndex].isEnabled = false
                dayCheckboxes[selectedStartDayIndex].isChecked = false

                binding.sourceDayName = dayNames[selectedStartDayIndex]
            }
        }

        // init first page
        dayNames.forEachIndexed { index, dayName ->
            binding.selectSourceDay.addView(
                    (LayoutInflater.from(context!!).inflate(
                            android.R.layout.simple_list_item_single_choice,
                            binding.selectSourceDay,
                            false
                    ) as CheckedTextView).let { dayView ->
                        dayView.text = dayName
                        dayView.setOnClickListener {
                            selectedStartDayIndex = index
                            bindSecondPage()

                            binding.flipper.setInAnimation(context!!, R.anim.wizard_open_step_in)
                            binding.flipper.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                            binding.flipper.displayedChild = 1
                        }

                        dayView
                    }
            )
        }

        // init second page
        bindSecondPage()

        binding.saveButton.setOnClickListener {
            val targetDays = mutableSetOf<Int>()

            dayCheckboxes.forEachIndexed { day, checkBox ->
                if (checkBox.isChecked && day != selectedStartDayIndex) {
                    targetDays.add(day)
                }
            }

            target.onCopyBlockedTimeAreasConfirmed(
                    sourceDay = selectedStartDayIndex,
                    targetDays = targetDays
            )

            dismissAllowingStateLoss()
        }

        // continue
        if (selectedStartDayIndex != -1) {
            binding.flipper.displayedChild = 1
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}

interface CopyBlockedTimeAreasDialogFragmentListener {
    fun onCopyBlockedTimeAreasConfirmed(sourceDay: Int, targetDays: Set<Int>)
}