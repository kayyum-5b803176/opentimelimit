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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentEditTimeLimitRuleDialogBinding
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.extensions.getParcelableCompat
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.CreateTimeLimitRuleAction
import io.timelimit.android.sync.actions.DeleteTimeLimitRuleAction
import io.timelimit.android.sync.actions.UpdateTimeLimitRuleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.util.DateUtil
import io.timelimit.android.ui.view.SelectDayViewHandlers
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import io.timelimit.android.util.TimeTextUtil
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import java.util.*


class EditTimeLimitRuleDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val PARAM_EXISTING_RULE = "a"
        private const val PARAM_CATEGORY_ID = "b"
        private const val SELF_LIMIT_MODE = "c"
        private const val DIALOG_TAG = "t"
        private const val STATE_RULE = "c"
        private const val REQUEST_START_TIME_OF_DAY = "editRule:startTimeOfDay"
        private const val REQUEST_END_TIME_OF_DAY = "editRule:endTimeOfDay"
        private const val REQUEST_EDIT_SESSION_LENGTH = "editRule:sessionLength"
        private const val REQUEST_EDIT_SESSION_PAUSE = "editRule:sessionPause"
        private const val REQUEST_EDIT_EXPIRE_DATE = "editRule:expire:data"

        fun newInstance(existingRule: TimeLimitRule, selfLimitMode: Boolean, listener: Fragment) = EditTimeLimitRuleDialogFragment()
            .apply {
                arguments = Bundle().apply {
                    putParcelable(PARAM_EXISTING_RULE, existingRule)
                    putBoolean(SELF_LIMIT_MODE, selfLimitMode)
                }

                setTargetFragment(listener, 0)
            }

        fun newInstance(categoryId: String, selfLimitMode: Boolean, listener: Fragment) = EditTimeLimitRuleDialogFragment()
            .apply {
                arguments = Bundle().apply {
                    putString(PARAM_CATEGORY_ID, categoryId)
                    putBoolean(SELF_LIMIT_MODE, selfLimitMode)
                }

                setTargetFragment(listener, 0)
            }
    }

    private var existingRule: TimeLimitRule? = null
    private var savedNewRule: TimeLimitRule? = null
    private var selfLimitMode: Boolean = false
    private lateinit var newRule: TimeLimitRule
    private lateinit var categoryId: String
    private lateinit var view: FragmentEditTimeLimitRuleDialogBinding

    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selfLimitMode = requireArguments().getBoolean(SELF_LIMIT_MODE)
        existingRule = savedInstanceState?.getParcelableCompat(PARAM_EXISTING_RULE)
            ?: arguments?.getParcelableCompat(PARAM_EXISTING_RULE)
        categoryId = existingRule?.categoryId ?: requireArguments().getString(PARAM_CATEGORY_ID)!!
    }

    private fun bindRule() {
        savedNewRule = newRule

        view.daySelection.selectedDays = BitSet.valueOf(longArrayOf(newRule.dayMask.toLong()))
        view.applyToExtraTime = newRule.applyToExtraTimeUsage

        val affectedDays = (0..6).map { (newRule.dayMask.toInt() shr it) and 1 }.sum()
        val maxDayDuration = if (newRule.perDay) 1 else affectedDays

        view.timeSpan.maxDays = 0.coerceAtLeast(maxDayDuration - 1)
        view.timeSpan.timeInMillis = newRule.maximumTimeInMillis.toLong()
        view.showWeeklyDailyOption = affectedDays >= 2 && newRule.maximumTimeInMillis > 0

        view.applyToWholeDay = newRule.appliesToWholeDay
        view.startTime = MinuteOfDay.format(newRule.startMinuteOfDay)
        view.endTime = MinuteOfDay.format(newRule.endMinuteOfDay)

        view.enableSessionDurationLimit = newRule.sessionDurationLimitEnabled
        view.sessionBreakText = TimeTextUtil.minutes(newRule.sessionPauseMilliseconds / (1000 * 60), requireContext())
        view.sessionLengthText = TimeTextUtil.minutes(newRule.sessionDurationMilliseconds / (1000 * 60), requireContext())

        view.typePerDay.isChecked = newRule.perDay
        view.typePerWeek.isChecked = !newRule.perDay

        view.applyToZeroDays = newRule.dayMask.toInt() == 0

        val existingRule = existingRule

        view.blockCurrentSelfLimitationParams =
            selfLimitMode && existingRule != null && !newRule.isAtLeastAsStrictAs(existingRule)

        newRule.expiresAt.also { expiresAt ->
            if (expiresAt == null) view.expireEnabled = false
            else {
                view.expireEnabled = true
                view.expireDateText = DateUtil.formatAbsoluteDate(requireContext(), expiresAt)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val listener = targetFragment as EditTimeLimitRuleDialogFragmentListener
        val database = DefaultAppLogic.with(requireContext()).database

        view = FragmentEditTimeLimitRuleDialogBinding.inflate(layoutInflater, container, false)

        auth.authenticatedUserOrChild.observe(viewLifecycleOwner, Observer {
            if (it == null || (it.type != UserType.Parent && !selfLimitMode)) {
                dismissAllowingStateLoss()
            }
        })

        if (existingRule == null) {
            view.isNewRule = true

            newRule = TimeLimitRule(
                    id = IdGenerator.generateId(),
                    categoryId = requireArguments().getString(PARAM_CATEGORY_ID)!!,
                    applyToExtraTimeUsage = false,
                    dayMask = 0,
                    maximumTimeInMillis = 1000 * 60 * 60,
                    startMinuteOfDay = TimeLimitRule.MIN_START_MINUTE,
                    endMinuteOfDay = TimeLimitRule.MAX_END_MINUTE,
                    sessionPauseMilliseconds = 0,
                    sessionDurationMilliseconds = 0,
                    perDay = true,
                    expiresAt = null
            )
        } else {
            view.isNewRule = false

            newRule = existingRule!!
        }

        view.isSelfLimitMode = selfLimitMode

        run {
            val restoredRule = savedInstanceState?.getParcelable<TimeLimitRule>(STATE_RULE)

            if (restoredRule != null) {
                newRule = restoredRule
            }
        }

        bindRule()
        view.daySelection.handlers = object: SelectDayViewHandlers {
            override fun updateDayChecked(day: Int) {
                newRule = newRule.copy(
                        dayMask = (newRule.dayMask.toInt() xor (1 shl day)).toByte()
                )

                bindRule()
            }
        }

        view.handlers = object: Handlers {
            override fun updateApplyToExtraTime(apply: Boolean) {
                newRule = newRule.copy(
                        applyToExtraTimeUsage = apply
                )

                bindRule()
            }

            override fun updateApplyToWholeDay(apply: Boolean) {
                if (apply) {
                    newRule = newRule.copy(
                            startMinuteOfDay = TimeLimitRule.MIN_START_MINUTE,
                            endMinuteOfDay = TimeLimitRule.MAX_END_MINUTE
                    )
                } else {
                    newRule = newRule.copy(
                            startMinuteOfDay = 10 * 60,
                            endMinuteOfDay = 16 * 60
                    )
                }

                bindRule()
            }

            override fun updateStartTime() {
                TimePickerDialogFragment.newInstance(
                        requestKey = REQUEST_START_TIME_OF_DAY,
                        startMinuteOfDay = newRule.startMinuteOfDay
                ).show(childFragmentManager)
            }

            override fun updateEndTime() {
                TimePickerDialogFragment.newInstance(
                        requestKey = REQUEST_END_TIME_OF_DAY,
                        startMinuteOfDay = newRule.endMinuteOfDay
                ).show(childFragmentManager)
            }

            override fun updateSessionDurationLimit(enable: Boolean) {
                if (enable) {
                    newRule = newRule.copy(
                            sessionDurationMilliseconds = 1000 * 60 * 30,
                            sessionPauseMilliseconds = 1000 * 60 * 10
                    )
                } else {
                    newRule = newRule.copy(
                            sessionDurationMilliseconds = 0,
                            sessionPauseMilliseconds = 0
                    )
                }

                bindRule()
            }

            override fun updateSessionLength() {
                DurationPickerDialogFragment.newInstance(
                        titleRes = R.string.category_time_limit_rules_session_limit_duration,
                        requestKey = REQUEST_EDIT_SESSION_LENGTH,
                        startTimeInMillis = newRule.sessionDurationMilliseconds
                ).show(childFragmentManager)
            }

            override fun updateSessionBreak() {
                DurationPickerDialogFragment.newInstance(
                        titleRes = R.string.category_time_limit_rules_session_limit_pause,
                        requestKey = REQUEST_EDIT_SESSION_PAUSE,
                        startTimeInMillis = newRule.sessionPauseMilliseconds
                ).show(childFragmentManager)
            }

            override fun updateExpireEnabled(enable: Boolean) {
                newRule = newRule.copy(
                    expiresAt = if (enable) LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else null
                )

                bindRule()
            }

            override fun updateExpireDate() {
                val expiresAt = Instant.ofEpochMilli(newRule.expiresAt ?: 0).atZone(ZoneId.systemDefault())

                DatePickerDialogFragment.newInstance(
                    REQUEST_EDIT_EXPIRE_DATE,
                    expiresAt.dayOfMonth,
                    expiresAt.monthValue,
                    expiresAt.year
                ).show(childFragmentManager)
            }

            override fun onSaveRule() {
                view.timeSpan.clearNumberPickerFocus()

                if (existingRule != null) {
                    if (existingRule != newRule) {
                        val updateAction = UpdateTimeLimitRuleAction(
                            ruleId = newRule.id,
                            maximumTimeInMillis = newRule.maximumTimeInMillis,
                            dayMask = newRule.dayMask,
                            applyToExtraTimeUsage = newRule.applyToExtraTimeUsage,
                            start = newRule.startMinuteOfDay,
                            end = newRule.endMinuteOfDay,
                            sessionDurationMilliseconds = newRule.sessionDurationMilliseconds,
                            sessionPauseMilliseconds = newRule.sessionPauseMilliseconds,
                            perDay = newRule.perDay,
                            expiresAt = newRule.expiresAt
                        )

                        if (!auth.tryDispatchParentAction(updateAction, allowAsChild = selfLimitMode)) {
                            return
                        }
                    }

                    listener.notifyRuleUpdated(existingRule!!, newRule)
                } else {
                    if (!auth.tryDispatchParentAction(
                                    action = CreateTimeLimitRuleAction(
                                            rule = newRule
                                    ),
                                    allowAsChild = true
                            )) {
                        return
                    }

                    listener.notifyRuleCreated()
                }

                dismissAllowingStateLoss()
            }

            override fun onDeleteRule() {
                if (!auth.tryDispatchParentAction(
                                DeleteTimeLimitRuleAction(
                                        ruleId = existingRule!!.id
                                )
                        )) {
                    return
                }

                listener.notifyRuleDeleted(existingRule!!)

                dismissAllowingStateLoss()
            }
        }

        view.timeSpan.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                if (newTimeInMillis.toInt() != newRule.maximumTimeInMillis) {
                    newRule = newRule.copy(maximumTimeInMillis = newTimeInMillis.toInt())

                    bindRule()
                }
            }

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    database.config().setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        view.typePerDay.setOnClickListener { newRule = newRule.copy(perDay = true); bindRule() }
        view.typePerWeek.setOnClickListener { newRule = newRule.copy(perDay = false); bindRule() }

        database.config().getEnableAlternativeDurationSelectionAsync().observe(viewLifecycleOwner, Observer {
            view.timeSpan.enablePickerMode(it)
        })

        if (existingRule != null) {
            database.timeLimitRules()
                    .getTimeLimitRuleByIdLive(existingRule!!.id).observe(viewLifecycleOwner, Observer {
                        if (it == null) {
                            // rule was deleted
                            dismissAllowingStateLoss()
                        } else {
                            if (it != existingRule) {
                                existingRule = it
                                newRule = it
                                bindRule()
                            }
                        }
                    })
        }

        return view.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childFragmentManager.setFragmentResultListener(REQUEST_START_TIME_OF_DAY, viewLifecycleOwner) { _, bundle ->
            val minuteOfDay = TimePickerDialogFragment.Result.fromBundle(bundle).minuteOfDay

            if (!MinuteOfDay.isValid(minuteOfDay)) {
                Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

                return@setFragmentResultListener
            } else if (minuteOfDay > newRule.endMinuteOfDay) {
                Toast.makeText(context, R.string.category_time_limit_rules_invalid_range, Toast.LENGTH_SHORT).show()
            } else {
                newRule = newRule.copy(startMinuteOfDay = minuteOfDay)
                bindRule()
            }
        }

        childFragmentManager.setFragmentResultListener(REQUEST_END_TIME_OF_DAY, viewLifecycleOwner) { _, bundle ->
            val minuteOfDay = TimePickerDialogFragment.Result.fromBundle(bundle).minuteOfDay

            if (!MinuteOfDay.isValid(minuteOfDay)) {
                Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
            } else if (minuteOfDay < newRule.startMinuteOfDay) {
                Toast.makeText(context, R.string.category_time_limit_rules_invalid_range, Toast.LENGTH_SHORT).show()
            } else {
                newRule = newRule.copy(endMinuteOfDay = minuteOfDay)
                bindRule()
            }
        }

        childFragmentManager.setFragmentResultListener(REQUEST_EDIT_SESSION_LENGTH, viewLifecycleOwner) { _, bundle ->
            newRule = newRule.copy(
                    sessionDurationMilliseconds = DurationPickerDialogFragment.Result.fromBundle(bundle).durationInMillis
            )

            bindRule()
        }

        childFragmentManager.setFragmentResultListener(REQUEST_EDIT_SESSION_PAUSE, viewLifecycleOwner) { _, bundle ->
            newRule = newRule.copy(
                    sessionPauseMilliseconds = DurationPickerDialogFragment.Result.fromBundle(bundle).durationInMillis
            )

            bindRule()
        }

        childFragmentManager.setFragmentResultListener(REQUEST_EDIT_EXPIRE_DATE, viewLifecycleOwner) { _, bundle ->
            val result = DatePickerDialogFragment.Result.fromBundle(bundle)
            val timeInMillis = result.localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            newRule = newRule.copy(expiresAt = timeInMillis)

            bindRule()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        // from https://stackoverflow.com/a/43602359
        dialog.setOnShowListener {
            BottomSheetBehavior.from(
                    dialog.findViewById<View>(R.id.design_bottom_sheet)
            ).setState(BottomSheetBehavior.STATE_EXPANDED)
        }

        return dialog
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val rule = savedNewRule

        if (rule != null) {
            outState.putParcelable(STATE_RULE, rule)
        }

        existingRule.let { existingRule ->
            if (existingRule != null) {
                outState.putParcelable(PARAM_EXISTING_RULE, existingRule)
            }
        }
    }
}

interface Handlers {
    fun updateApplyToExtraTime(apply: Boolean)
    fun updateApplyToWholeDay(apply: Boolean)
    fun updateStartTime()
    fun updateEndTime()
    fun updateSessionDurationLimit(enable: Boolean)
    fun updateSessionLength()
    fun updateSessionBreak()
    fun updateExpireEnabled(enable: Boolean)
    fun updateExpireDate()
    fun onSaveRule()
    fun onDeleteRule()
}

interface EditTimeLimitRuleDialogFragmentListener {
    fun notifyRuleCreated()
    fun notifyRuleDeleted(oldRule: TimeLimitRule)
    fun notifyRuleUpdated(oldRule: TimeLimitRule, newRule: TimeLimitRule)
}