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
package io.timelimit.android.ui.manage.category.settings.addusedtime

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.AddUsedTimeDialogBinding
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.date.getMinuteOfWeek
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddUsedTimeActionItem
import io.timelimit.android.sync.actions.AddUsedTimeActionItemAdditionalCountingSlot
import io.timelimit.android.sync.actions.AddUsedTimeActionVersion2
import io.timelimit.android.sync.actions.apply.ApplyActionUtil
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import io.timelimit.android.util.TimeTextUtil
import java.util.*

class AddUsedTimeDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AddUsedTimeDialogFragment"
        private const val LOG_TAG = "AddUsedTimeDialog"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(childId: String, categoryId: String) = AddUsedTimeDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val childId = arguments!!.getString(CHILD_ID)!!
        val categoryId = arguments!!.getString(CATEGORY_ID)!!
        val binding = AddUsedTimeDialogBinding.inflate(inflater, container, false)
        val auth = (activity as ActivityViewModelHolder).getActivityViewModel()
        val logic = DefaultAppLogic.with(context!!)

        // check if parent is signed in
        auth.authenticatedUserOrChild.observe(viewLifecycleOwner, Observer {
            val validParent = it?.type == UserType.Parent
            val validChild = it?.type == UserType.Child && childId == it.id
            val valid = validParent || validChild

            if (!valid) dismissAllowingStateLoss()
        })

        // load category title/ check if it exists and show it
        logic.database.category().getCategoryByChildIdAndId(childId = childId, categoryId = categoryId).observe(viewLifecycleOwner, Observer {
            if (it == null) { dismissAllowingStateLoss(); return@Observer }

            binding.categoryTitle = it.title
        })

        // make the mode switcher work
        binding.timeToAdd.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) = Unit

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    auth.logic.database.config().setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        auth.logic.database.config().getEnableAlternativeDurationSelectionAsync().observe(viewLifecycleOwner, Observer {
            binding.timeToAdd.enablePickerMode(it)
        })

        binding.confirmBtn.setOnClickListener {
            val timeToAdd = binding.timeToAdd.timeInMillis

            if (timeToAdd > 0L) {
                if (auth.isParentOrChildAuthenticated(childId = childId)) {
                    val timeInMillis = logic.timeApi.getCurrentTimeInMillis()
                    val savedContext = context!!.applicationContext
                    val categoryTitle = binding.categoryTitle

                    runAsync {
                        try {
                            val childUser = logic.database.user().getUserByIdCoroutine(childId)
                            if (childUser?.type != UserType.Child) throw IllegalArgumentException()

                            val rules = logic.database.timeLimitRules().getTimeLimitRulesByCategoryCoroutine(categoryId = categoryId)

                            val minuteOfWeek = getMinuteOfWeek(timeInMillis, TimeZone.getTimeZone(childUser.timeZone))
                            val localDate = DateInTimezone.getLocalDate(
                                    timeInMillis = timeInMillis,
                                    timeZone = TimeZone.getTimeZone(childUser.timeZone)
                            )

                            val dayOfEpoch = localDate.toEpochDay()
                            val dayOfWeek = DateInTimezone.convertDayOfWeek(localDate.dayOfWeek)
                            val minuteOfDay = minuteOfWeek % MinuteOfDay.LENGTH

                            val additionalSlots = rules
                                    .filter { /* related to today */ it.dayMask.toInt() and (1 shl dayOfWeek) != 0 }
                                    .filter { /* related to the current time */ it.startMinuteOfDay <= minuteOfDay && minuteOfDay <= it.endMinuteOfDay }
                                    .filterNot { it.appliesToWholeDay }
                                    .map { AddUsedTimeActionItemAdditionalCountingSlot(it.startMinuteOfDay, it.endMinuteOfDay) }
                                    .toSet()

                            ApplyActionUtil.applyAppLogicAction(
                                    action = AddUsedTimeActionVersion2(
                                            dayOfEpoch = dayOfEpoch.toInt(),
                                            items = listOf(
                                                    AddUsedTimeActionItem(
                                                            categoryId = categoryId,
                                                            timeToAdd = timeToAdd.toInt(),
                                                            extraTimeToSubtract = 0,
                                                            additionalCountingSlots = additionalSlots,
                                                            sessionDurationLimits = emptySet()
                                                    )
                                            ),
                                            trustedTimestamp = 0
                                    ),
                                    appLogic = logic,
                                    ignoreIfDeviceIsNotConfigured = true
                            )

                            Toast.makeText(
                                    savedContext,
                                    savedContext.getString(R.string.add_used_time_confirmation_toast, TimeTextUtil.time(timeToAdd.toInt(), savedContext), categoryTitle),
                                    Toast.LENGTH_LONG
                            ).show()
                        } catch (ex: Exception) {
                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "failed to add used time", ex)
                            }

                            Toast.makeText(savedContext, R.string.error_general, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }

            dismissAllowingStateLoss()
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}