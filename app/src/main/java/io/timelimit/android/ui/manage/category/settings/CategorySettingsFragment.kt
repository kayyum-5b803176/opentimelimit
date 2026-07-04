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
package io.timelimit.android.ui.manage.category.settings

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.extensions.mapToTimezone
import io.timelimit.android.databinding.FragmentCategorySettingsBinding
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SetCategoryExtraTimeAction
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.settings.addusedtime.AddUsedTimeDialogFragment
import io.timelimit.android.ui.manage.category.settings.networks.ManageCategoryNetworksView
import io.timelimit.android.ui.manage.category.settings.timewarning.CategoryTimeWarningStatus
import io.timelimit.android.ui.manage.category.settings.timewarning.CategoryTimeWarningView
import io.timelimit.android.ui.util.bind

class CategorySettingsFragment : Fragment() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"
        private const val TIME_WARNING_STATUS = "timeWarningStatus"

        fun newInstance(childId: String, categoryId: String) = CategorySettingsFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    private val appLogic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val childId: String get() = requireArguments().getString(CHILD_ID)!!
    private val categoryId: String get() = requireArguments().getString(CATEGORY_ID)!!

    private val timeWarningStatus = MutableLiveData<CategoryTimeWarningStatus>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        timeWarningStatus.value = savedInstanceState?.getParcelable(TIME_WARNING_STATUS) ?: CategoryTimeWarningStatus.default
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentCategorySettingsBinding.inflate(inflater, container, false)

        val categoryEntry = appLogic.database.category().getCategoryByChildIdAndId(childId, categoryId)
        val timeWarnings = appLogic.database.timeWarning().getItemsByCategoryIdLive(categoryId)

        categoryEntry.observe(viewLifecycleOwner) {
            if (it != null) {
                timeWarningStatus.value?.let { old ->
                    timeWarningStatus.value = old.update(it)
                }
            }
        }

        timeWarnings.observe(viewLifecycleOwner) {
            if (it != null) {
                timeWarningStatus.value?.let { old ->
                    timeWarningStatus.value = old.update(it)
                }
            }
        }

        val childDate = appLogic.database.user().getChildUserByIdLive(childId).mapToTimezone().switchMap { timezone ->
            liveDataFromFunction (1000 * 10L) { DateInTimezone.newInstance(appLogic.timeApi.getCurrentTimeInMillis(), timezone) }
        }.ignoreUnchanged()

        val currentExtraTime = categoryEntry.switchMap { category ->
            childDate.map { date ->
                category?.getExtraTime(date.dayOfEpoch)
            }
        }.ignoreUnchanged()

        val currentExtraTimeBoundToDate = currentExtraTime.map { it != null && it != 0L }.and(
                categoryEntry.map { it?.extraTimeDay != null && it.extraTimeDay != -1 }
        ).ignoreUnchanged()

        ManageCategoryForUnassignedApps.bind(
                binding = binding.categoryForUnassignedApps,
                lifecycleOwner = this,
                categoryId = categoryId,
                childId = childId,
                database = appLogic.database,
                auth = auth,
                fragmentManager = parentFragmentManager
        )

        CategoryBatteryLimitView.bind(
                binding = binding.batteryLimit,
                lifecycleOwner = this,
                category = categoryEntry,
                auth = auth,
                categoryId = categoryId,
                fragmentManager = parentFragmentManager
        )

        ParentCategoryView.bind(
                binding = binding.parentCategory,
                lifecycleOwner = this,
                categoryId = categoryId,
                childId = childId,
                database = appLogic.database,
                fragmentManager = parentFragmentManager,
                auth = auth
        )

        CategoryNotificationFilter.bind(
                view = binding.notificationFilter,
                lifecycleOwner = this,
                fragmentManager = parentFragmentManager,
                auth = auth,
                categoryLive = categoryEntry,
                childId = childId,
                deviceLive = appLogic.deviceEntry
        )

        CategoryTimeWarningView.bind(
            view = binding.timeWarnings,
            auth = auth,
            statusLive = timeWarningStatus,
            lifecycleOwner = this,
            fragmentManager = parentFragmentManager,
            categoryId = categoryId
        )

        ManageCategoryNetworksView.bind(
                view = binding.networks,
                auth = auth,
                lifecycleOwner = viewLifecycleOwner,
                fragmentManager = parentFragmentManager,
                fragment = this,
                permissionRequestCode = PERMISSION_REQUEST_CODE,
                categoryId = categoryId,
                categoryLive = categoryEntry
        )

        binding.btnDeleteCategory.setOnClickListener { deleteCategory() }
        binding.editCategoryTitleGo.setOnClickListener { renameCategory() }
        binding.addUsedTimeBtn.setOnClickListener { addUsedTime() }

        binding.extraTimeTitle.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.category_settings_extra_time_title,
                    text = R.string.category_settings_extra_time_info
            ).show(parentFragmentManager)
        }

        fun updateEditExtraTimeConfirmButtonVisibility() {
            val roundedCurrentTimeInMillis = currentExtraTime.value?.let { (it / (1000 * 60)) * (1000 * 60) } ?: 0
            val newLimitToToday = binding.switchLimitExtraTimeToToday.isChecked
            val newTimeInMillis = binding.extraTimeSelection.timeInMillis

            val timeDiffers = newTimeInMillis != roundedCurrentTimeInMillis
            val dayDiffers = newLimitToToday != (currentExtraTimeBoundToDate.value ?: false)

            binding.extraTimeBtnOk.visibility = if (timeDiffers || dayDiffers)
                View.VISIBLE
            else
                View.GONE
        }

        currentExtraTime.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                val roundedCurrentTimeInMillis = (it / (1000 * 60)) * (1000 * 60)

                if (binding.extraTimeSelection.timeInMillis != roundedCurrentTimeInMillis) {
                    binding.extraTimeSelection.timeInMillis = roundedCurrentTimeInMillis

                    updateEditExtraTimeConfirmButtonVisibility()
                }
            }
        })

        currentExtraTimeBoundToDate.observe(viewLifecycleOwner, Observer {
            if (binding.switchLimitExtraTimeToToday.isChecked != it) {
                binding.switchLimitExtraTimeToToday.isChecked = it

                updateEditExtraTimeConfirmButtonVisibility()
            }
        })

        binding.extraTimeBtnOk.setOnClickListener {
            binding.extraTimeSelection.clearNumberPickerFocus()

            val newExtraTime = binding.extraTimeSelection.timeInMillis

            if (
                    auth.tryDispatchParentAction(
                            SetCategoryExtraTimeAction(
                                    categoryId = categoryId,
                                    newExtraTime = newExtraTime,
                                    extraTimeDay = (if (binding.switchLimitExtraTimeToToday.isChecked) childDate.value?.dayOfEpoch else null) ?: -1
                            )
                    )
            ) {
                Snackbar.make(binding.root, R.string.category_settings_extra_time_change_toast, Snackbar.LENGTH_SHORT).show()

                binding.extraTimeBtnOk.visibility = View.GONE
            }
        }

        binding.extraTimeSelection.bind(appLogic.database, viewLifecycleOwner) {
            updateEditExtraTimeConfirmButtonVisibility()
        }

        binding.switchLimitExtraTimeToToday.setOnCheckedChangeListener { _, _ ->
            updateEditExtraTimeConfirmButtonVisibility()
        }

        return binding.root
    }

    private fun renameCategory() {
        if (auth.requestAuthenticationOrReturnTrue()) {
            RenameCategoryDialogFragment.newInstance(childId = childId, categoryId = categoryId).show(parentFragmentManager)
        }
    }

    private fun deleteCategory() {
        if (auth.requestAuthenticationOrReturnTrue()) {
            DeleteCategoryDialogFragment.newInstance(childId = childId, categoryId = categoryId).show(parentFragmentManager)
        }
    }

    private fun addUsedTime() {
        if (auth.requestAuthenticationOrReturnTrueAllowChild(childId = childId)) {
            AddUsedTimeDialogFragment.newInstance(
                    childId = childId,
                    categoryId = categoryId
            ).show(parentFragmentManager)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.find { it != PackageManager.PERMISSION_GRANTED } != null) {
                Toast.makeText(requireContext(), R.string.generic_runtime_permission_rejected, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        timeWarningStatus.value?.let { outState.putParcelable(TIME_WARNING_STATUS, it) }
    }
}
