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

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.CategoryNotificationFilterBinding
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.util.TimeTextUtil

object CategoryNotificationFilter {
    fun bind(
            view: CategoryNotificationFilterBinding,
            auth: ActivityViewModel,
            categoryLive: LiveData<Category?>,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager,
            childId: String,
            deviceLive: LiveData<Device?>
    ) {
        val context = view.root.context

        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.category_notification_filter_title,
                    text = R.string.category_notification_filter_text
            ).show(fragmentManager)
        }

        view.button.setOnClickListener {
            val category = categoryLive.value

            if (auth.requestAuthenticationOrReturnTrueAllowChild(childId) && category != null) {
                ManageNotificationFilterDialogFragment.newInstance(
                    childId = childId,
                    categoryId = category.id
                ).show(fragmentManager)
            }
        }

        mergeLiveData(categoryLive, deviceLive).observe(lifecycleOwner) { (category, device) ->
            val hasPermission = device?.currentNotificationAccessPermission == NewPermissionStatus.Granted
            val shouldBeChecked = category?.blockAllNotifications ?: false
            val blockDelay = category?.blockNotificationDelay ?: 0

            view.status = if (shouldBeChecked) {
                if (blockDelay == 0L) {
                    context.getString(R.string.category_notification_filter_summary_enabled_no_delay)
                } else {
                    context.getString(
                        R.string.category_notification_filter_summary_enabled_with_delay,
                        TimeTextUtil.seconds((blockDelay / 1000L).toInt(), context)
                    )
                }
            } else if (hasPermission) {
                context.getString(R.string.category_notification_filter_summary_has_permission)
            } else {
                context.getString(R.string.category_notification_filter_summary_disabled)
            }
        }
    }
}