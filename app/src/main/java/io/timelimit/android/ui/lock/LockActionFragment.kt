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
package io.timelimit.android.ui.lock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.timelimit.android.R
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.derived.DeviceRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.databinding.LockActionFragmentBinding
import io.timelimit.android.databinding.LockFragmentCategoryButtonBinding
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.logic.BlockingLevel
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.IncrementCategoryExtraTimeAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.settings.networks.RequestWifiPermission
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import io.timelimit.android.ui.manage.child.category.specialmode.SetCategorySpecialModeFragment
import io.timelimit.android.ui.manage.child.category.specialmode.SpecialModeDialogMode
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import java.util.*

class LockActionFragment : Fragment() {
    companion object {
        private const val LOCATION_REQUEST_CODE = 1
    }

    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private lateinit var binding: LockActionFragmentBinding
    private val model: LockModel by activityViewModels()

    private fun setupHandlers(deviceId: String, userRelatedData: UserRelatedData, blockedCategoryId: String?) {
        binding.handlers = object: Handlers {
            override fun openMainApp() {
                val user = auth.getAuthenticatedUser()

                startActivity(
                    if (user == null)
                        Intent(context, MainActivity::class.java)
                    else
                        MainActivity.getAuthHandoverIntent(
                            requireContext(),
                            user
                        )
                )
            }

            override fun allowTemporarily() {
                if (auth.requestAuthenticationOrReturnTrue()) model.allowAppTemporarily()
            }

            override fun disableTemporarilyLockForAllCategories() {
                auth.tryDispatchParentActions(
                        userRelatedData.categories
                                .filter { it.category.temporarilyBlocked }
                                .map {
                                    UpdateCategoryTemporarilyBlockedAction(
                                            categoryId = it.category.id,
                                            blocked = false,
                                            endTime = null
                                    )
                                }
                )
            }

            override fun disableTemporarilyLockForCurrentCategory() {
                blockedCategoryId ?: return

                auth.tryDispatchParentAction(
                        UpdateCategoryTemporarilyBlockedAction(
                                categoryId = blockedCategoryId,
                                blocked = false,
                                endTime = null
                        )
                )
            }

            override fun requestLocationPermission() {
                RequestWifiPermission.doRequest(this@LockActionFragment, LOCATION_REQUEST_CODE, auth.logic.platformIntegration)
            }

            override fun disableLimitsTemporarily() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    if (blockedCategoryId != null) {
                        SetCategorySpecialModeFragment.newInstance(
                            childId = userRelatedData.user.id,
                            categoryId = blockedCategoryId,
                            mode = SpecialModeDialogMode.DisableLimitsOnly
                        ).show(parentFragmentManager)
                    }
                }
            }
        }
    }

    private fun bindAddToCategoryOptions(userRelatedData: UserRelatedData, blockedPackageName: String) {
        binding.addToCategoryOptions.removeAllViews()

        userRelatedData.sortedCategories().forEach { (_, category) ->
            LockFragmentCategoryButtonBinding.inflate(LayoutInflater.from(context), binding.addToCategoryOptions, true).let { button ->
                button.title = category.category.title
                button.button.setOnClickListener { _ ->
                    auth.tryDispatchParentAction(
                            AddCategoryAppsAction(
                                    categoryId = category.category.id,
                                    packageNames = listOf(blockedPackageName)
                            )
                    )
                }
            }
        }

        LockFragmentCategoryButtonBinding.inflate(LayoutInflater.from(context), binding.addToCategoryOptions, true).let { button ->
            button.title = getString(R.string.create_category_title)
            button.button.setOnClickListener {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    CreateCategoryDialogFragment
                            .newInstance(childId = userRelatedData.user.id)
                            .show(parentFragmentManager)
                }
            }
        }
    }

    private fun bindExtraTimeView(deviceRelatedData: DeviceRelatedData, categoryId: String, timeZone: TimeZone) {
        binding.extraTimeBtnOk.setOnClickListener {
            binding.extraTimeSelection.clearNumberPickerFocus()

            if (auth.requestAuthenticationOrReturnTrue()) {
                val extraTimeToAdd = binding.extraTimeSelection.timeInMillis

                if (extraTimeToAdd > 0) {
                    binding.extraTimeBtnOk.isEnabled = false

                    val date = DateInTimezone.newInstance(auth.logic.timeApi.getCurrentTimeInMillis(), timeZone)

                    auth.tryDispatchParentAction(IncrementCategoryExtraTimeAction(
                            categoryId = categoryId,
                            addedExtraTime = extraTimeToAdd,
                            extraTimeDay = if (binding.switchLimitExtraTimeToToday.isChecked) date.dayOfEpoch else -1
                    ))

                    binding.extraTimeBtnOk.isEnabled = true
                }
            }
        }
    }

    private fun initExtraTimeView() {
        binding.extraTimeTitle.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.lock_extratime_title,
                    text = R.string.lock_extratime_text
            ).show(parentFragmentManager)
        }

        model.enableAlternativeDurationSelection.observe(viewLifecycleOwner) { binding.extraTimeSelection.enablePickerMode(it) }

        binding.extraTimeSelection.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                binding.extraTimeBtnOk.visibility = if (newTimeInMillis == 0L) View.GONE else View.VISIBLE
            }

            override fun setEnablePickerMode(enable: Boolean) { model.setEnablePickerMode(enable) }
        }
    }

    private fun initGrantPermissionView() {
        model.missingNetworkIdPermission.observe(viewLifecycleOwner) { binding.missingNetworkIdPermission = it }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LockActionFragmentBinding.inflate(layoutInflater, container, false)

        model.osClockInMillis.observe(viewLifecycleOwner) { systemTime ->
            binding.currentTime = DateUtils.formatDateTime(
                    context,
                    systemTime!!,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY
            )
        }

        binding.appTitle = model.title ?: "???"

        initExtraTimeView()
        initGrantPermissionView()

        model.content.observe(viewLifecycleOwner) { content ->
            when (content) {
                LockscreenContent.Close -> {
                    binding.reason = BlockingReason.None
                    binding.handlers = null

                    requireActivity().finish()
                }
                is LockscreenContent.Blocked -> {
                    binding.reason = content.reason
                    binding.blockedKindLabel = when (content.level) {
                        BlockingLevel.Activity -> "Activity"
                        BlockingLevel.App -> "App"
                    }

                    when (content) {
                        is LockscreenContent.Blocked.BlockedCategory -> {
                            binding.appCategoryTitle = content.appCategoryTitle
                            setupHandlers(
                                    deviceId = content.deviceId,
                                    blockedCategoryId = content.blockedCategoryId,
                                    userRelatedData = content.userRelatedData
                            )
                            bindExtraTimeView(
                                    deviceRelatedData = content.deviceRelatedData,
                                    categoryId = content.blockedCategoryId,
                                    timeZone = content.userRelatedData.timeZone
                            )
                        }
                        is LockscreenContent.Blocked.BlockDueToNoCategory -> {
                            binding.appCategoryTitle = null

                            setupHandlers(
                                    deviceId = content.deviceId,
                                    blockedCategoryId = null,
                                    userRelatedData = content.userRelatedData
                            )

                            bindAddToCategoryOptions(
                                    userRelatedData = content.userRelatedData,
                                    blockedPackageName = content.appPackageName
                            )
                        }
                    }.let {/* require handling all paths */}
                }
            }.let {/* require handling all paths */}
        }

        return binding.root
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.find { it != PackageManager.PERMISSION_GRANTED } != null) {
            Toast.makeText(requireContext(), R.string.generic_runtime_permission_rejected, Toast.LENGTH_LONG).show()
        }
    }
}

interface Handlers {
    fun openMainApp()
    fun allowTemporarily()
    fun disableTemporarilyLockForCurrentCategory()
    fun disableTemporarilyLockForAllCategories()
    fun requestLocationPermission()
    fun disableLimitsTemporarily()
}
