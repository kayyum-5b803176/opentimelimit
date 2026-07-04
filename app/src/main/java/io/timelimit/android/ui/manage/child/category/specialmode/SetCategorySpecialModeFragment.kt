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

package io.timelimit.android.ui.manage.child.category.specialmode

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.timelimit.android.R
import io.timelimit.android.databinding.SpecialModeDialogBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

class SetCategorySpecialModeFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "SetCategorySpecialModeFragment"

        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"
        private const val MODE = "mode"

        private const val PAGE_TYPE = 0
        private const val PAGE_SUGGESTION = 1
        private const val PAGE_CLOCK = 2
        private const val PAGE_CALENDAR = 3

        fun newInstance(childId: String, categoryId: String, mode: SpecialModeDialogMode) = SetCategorySpecialModeFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
                putSerializable(MODE, mode)
            }
        }
    }

    private val model: SetCategorySpecialModeModel by viewModels()
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = object: BottomSheetDialog(requireContext(), theme) {
        init {
            onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!model.goBack()) dismissAllowingStateLoss()
                }
            })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val childId = requireArguments().getString(CHILD_ID)!!
        val categoryId = requireArguments().getString(CATEGORY_ID)!!
        val mode = requireArguments().getSerializable(MODE)!! as SpecialModeDialogMode

        model.init(childId = childId, categoryId = categoryId, mode = mode)

        if (mode == SpecialModeDialogMode.SelfLimitAdd) {
            auth.authenticatedUserOrChild.observe(viewLifecycleOwner) { if (it == null) dismissAllowingStateLoss() }
        } else {
            auth.authenticatedUser.observe(viewLifecycleOwner) { if (it == null) dismissAllowingStateLoss() }
        }

        val binding = SpecialModeDialogBinding.inflate(inflater, container, false)
        val flipper = binding.flipper

        fun openAnimation() {
            flipper.setInAnimation(requireContext(), R.anim.wizard_open_step_in)
            flipper.setOutAnimation(requireContext(), R.anim.wizard_open_step_out)
        }

        fun closeAnimation() {
            flipper.setInAnimation(requireContext(), R.anim.wizard_close_step_in)
            flipper.setOutAnimation(requireContext(), R.anim.wizard_close_step_out)
        }

        fun setPage(page: Int) {
            if (flipper.displayedChild != page) {
                if (flipper.displayedChild > page) closeAnimation() else openAnimation()

                flipper.displayedChild = page
            }
        }

        val specialModeOptionAdapter = SpecialModeOptionAdapter()

        model.content.observe(viewLifecycleOwner) { content ->
            val screen = content?.screen

            when (screen) {
                null -> { dismiss() }
                is SetCategorySpecialModeModel.Screen.SelectType -> {
                    setPage(PAGE_TYPE)
                    binding.title = content.categoryTitle
                }
                is SetCategorySpecialModeModel.Screen.WithType -> {
                    binding.title = getString(
                            when (screen.type) {
                                SetCategorySpecialModeModel.Type.BlockTemporarily -> R.string.manage_child_special_mode_wizard_block_title
                                SetCategorySpecialModeModel.Type.DisableLimits -> R.string.manage_child_special_mode_wizard_disable_limits_title
                            },
                            content.categoryTitle
                    )

                    when (screen) {
                        is SetCategorySpecialModeModel.Screen.WithType.SuggestionList -> {
                            if (flipper.displayedChild == 0 && mode == SpecialModeDialogMode.DisableLimitsOnly) flipper.displayedChild = 1
                            else setPage(PAGE_SUGGESTION)

                            specialModeOptionAdapter.items = screen.options
                        }
                        is SetCategorySpecialModeModel.Screen.WithType.CalendarScreen -> setPage(PAGE_CALENDAR)
                        is SetCategorySpecialModeModel.Screen.WithType.ClockScreen -> setPage(PAGE_CLOCK)
                    }
                }
            }.let {/* require handling all cases */}
        }

        binding.blockTemporarilyOption.setOnClickListener { model.selectType(SetCategorySpecialModeModel.Type.BlockTemporarily) }
        binding.disableLimitsOption.setOnClickListener { model.selectType(SetCategorySpecialModeModel.Type.DisableLimits) }

        binding.isAddLimitMode = mode == SpecialModeDialogMode.SelfLimitAdd

        binding.suggestionList.also {
            it.adapter = specialModeOptionAdapter
            it.layoutManager = LinearLayoutManager(requireContext())
        }

        specialModeOptionAdapter.listener = object: SpecialModeOptionListener {
            override fun onItemClicked(item: SpecialModeOption) {
                model.applySelection(item, auth)
            }
        }

        run {
            fun readClockTime(timeZone: String, now: Long) = binding.timePicker.let {
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(now),
                        ZoneId.of(timeZone)
                )
                        .toLocalDate()
                        .atStartOfDay(ZoneId.of(timeZone))
                        .plusHours(it.currentHour.toLong())
                        .plusMinutes(it.currentMinute.toLong())
                        .toEpochSecond() * 1000
            }

            fun update() {
                val content = model.content.value
                val minTime = model.minTimestamp.value

                if (content?.screen is SetCategorySpecialModeModel.Screen.WithType.ClockScreen && minTime != null) {
                    val currentSelectedTime = readClockTime(content.childTimezone, model.now())
                    val isEnabled = currentSelectedTime > minTime

                    binding.confirmTimePickerButton.isEnabled = isEnabled

                    if (isEnabled) {
                        binding.confirmTimePickerButton.setOnClickListener {
                            model.applySelection(timeInMillis = currentSelectedTime, auth = auth)
                        }
                    }
                } else binding.confirmTimePickerButton.isEnabled = false
            }

            model.content.observe(viewLifecycleOwner) { update() }
            model.minTimestamp.observe(viewLifecycleOwner) { update() }
            model.nowLive.observe(viewLifecycleOwner) { update() }
            binding.timePicker.setOnTimeChangedListener { _, _, _ -> update() }
        }

        run {
            fun readCalendarTime(timeZone: String) = binding.datePicker.let {
                LocalDate.of(it.year, it.month + 1, it.dayOfMonth)
                        .atStartOfDay(ZoneId.of(timeZone))
                        .toEpochSecond() * 1000
            }

            fun update() {
                val content = model.content.value
                val minTime = model.minTimestamp.value

                if (content?.screen is SetCategorySpecialModeModel.Screen.WithType.CalendarScreen && minTime != null) {
                    val currentSelectedTime = readCalendarTime(content.childTimezone)
                    val isEnabled = currentSelectedTime > minTime

                    binding.confirmDatePickerButton.isEnabled = isEnabled

                    if (isEnabled) {
                        binding.confirmDatePickerButton.setOnClickListener {
                            val currentSelectedTimeNew = readCalendarTime(content.childTimezone)

                            if (currentSelectedTimeNew > minTime) {
                                model.applySelection(timeInMillis = currentSelectedTimeNew, auth = auth)
                            } else update()
                        }
                    }
                } else binding.confirmDatePickerButton.isEnabled = false
            }

            model.content.observe(viewLifecycleOwner) { update() }
            model.minTimestamp.observe(viewLifecycleOwner) { update() }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                binding.datePicker.setOnDateChangedListener { _, _, _, _ -> update() }
            }
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}