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

package io.timelimit.android.ui.manage.category.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.ManageNotificationFilterDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.sync.actions.UpdateCategoryBlockAllNotificationsAction
import io.timelimit.android.ui.main.ActivityViewModelHolder

class ManageNotificationFilterDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "EnableNotificationFilterDialogFragment"
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"
        private const val PICKER_SCALE = 1000

        fun newInstance(childId: String, categoryId: String) = ManageNotificationFilterDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    private val model: ManageNotificationFilterModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ManageNotificationFilterDialogFragmentBinding.inflate(inflater, container, false)
        val auth = activity as ActivityViewModelHolder
        val childId = requireArguments().getString(CHILD_ID)!!
        val categoryId = requireArguments().getString(CATEGORY_ID)!!

        binding.delayPicker.minValue = 1
        binding.delayPicker.maxValue = Category.MAX_NOTIFICATION_BLOCK_DELAY.toInt() / PICKER_SCALE

        binding.enableSwitch.isEnabled = false
        binding.delaySwitch.isEnabled = false
        binding.delayPicker.isEnabled = false

        fun updateDelaySwitchEnabled() {
            binding.delaySwitch.isEnabled = binding.enableSwitch.isEnabled && binding.enableSwitch.isChecked
            binding.delayPicker.isEnabled = binding.delaySwitch.isEnabled
        }

        binding.showDelayPicker = binding.delaySwitch.isChecked
        binding.delaySwitch.setOnCheckedChangeListener { _, isChecked -> binding.showDelayPicker = isChecked }

        binding.enableSwitch.setOnCheckedChangeListener { _, isChecked -> updateDelaySwitchEnabled() }

        model.init(categoryId = categoryId, childId = childId)

        val data = mergeLiveDataWaitForValues(
            model.categoryEntry,
            model.hasPermission,
            auth.getActivityViewModel().authenticatedUserOrChild
        )

        data.observe(viewLifecycleOwner) { (entry, hasPermission, authenticatedUserOrChild) ->
            if (authenticatedUserOrChild == null || (authenticatedUserOrChild.type != UserType.Parent && authenticatedUserOrChild.id != childId)) {
                dismissAllowingStateLoss()
            } else if (entry == null) dismissAllowingStateLoss() else {
                val parentAuthenticated = authenticatedUserOrChild.type == UserType.Parent
                val validAuth = parentAuthenticated || !entry.blockAllNotifications
                val lastBoundEntry = model.lastBoundEntry

                if (lastBoundEntry == null || lastBoundEntry.blockAllNotifications != entry.blockAllNotifications) {
                    binding.enableSwitch.isChecked = entry.blockAllNotifications
                }

                if (lastBoundEntry == null || lastBoundEntry.blockNotificationDelay != entry.blockNotificationDelay) {
                    binding.delaySwitch.isChecked = entry.blockNotificationDelay >= PICKER_SCALE

                    binding.delayPicker.value = (entry.blockNotificationDelay / PICKER_SCALE)
                        .coerceAtLeast(1)
                        .coerceAtMost(Category.MAX_NOTIFICATION_BLOCK_DELAY / PICKER_SCALE)
                        .toInt()
                }

                model.lastBoundEntry = entry

                binding.showMissingPermissionText = !hasPermission
                binding.enableSwitch.isEnabled = (hasPermission || entry.blockAllNotifications) && validAuth

                updateDelaySwitchEnabled()
            }
        }

        binding.saveBtn.setOnClickListener {
            val lastBoundEntry = model.lastBoundEntry
            val blockAllNotifications = binding.enableSwitch.isChecked

            if (lastBoundEntry != null) {
                val blockNotificationDelay = if (binding.delaySwitch.isChecked) {
                    binding.delayPicker.value.toLong() * PICKER_SCALE
                } else 0

                val isParent = auth.getActivityViewModel().authenticatedUserOrChild.value?.type == UserType.Parent
                val hasPermission = isParent || !lastBoundEntry.blockAllNotifications
                val enableChanged = lastBoundEntry.blockAllNotifications != blockAllNotifications
                val delayChanged = blockNotificationDelay != lastBoundEntry.blockNotificationDelay
                val hasChange = enableChanged || delayChanged

                if (hasPermission && hasChange) {
                    auth.getActivityViewModel().tryDispatchParentAction(
                        action = UpdateCategoryBlockAllNotificationsAction(
                            categoryId = categoryId,
                            blocked = blockAllNotifications,
                            blockDelay = if (delayChanged) blockNotificationDelay else null
                        ),
                        allowAsChild = true
                    )

                    Toast.makeText(requireContext(), R.string.category_notification_filter_save_toast, Toast.LENGTH_SHORT).show()
                }
            }

            dismissAllowingStateLoss()
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}