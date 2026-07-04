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
package io.timelimit.android.ui.manage.category.usagehistory

import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.UsedTimeListItem
import io.timelimit.android.databinding.FragmentUsageHistoryItemBinding
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.util.TimeTextUtil
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import java.util.*
import kotlin.properties.Delegates

class UsageHistoryAdapter: PagedListAdapter<UsedTimeListItem, UsageHistoryViewHolder>(diffCallback) {
    companion object {
        private val diffCallback = object: DiffUtil.ItemCallback<UsedTimeListItem>() {
            override fun areContentsTheSame(oldItem: UsedTimeListItem, newItem: UsedTimeListItem) = oldItem == newItem
            override fun areItemsTheSame(oldItem: UsedTimeListItem, newItem: UsedTimeListItem) =
                    (oldItem.day == newItem.day) && (oldItem.startMinuteOfDay == newItem.startMinuteOfDay) &&
                            (oldItem.endMinuteOfDay == newItem.endMinuteOfDay) && (oldItem.maxSessionDuration == newItem.maxSessionDuration)
        }
    }

    var showCategoryTitle: Boolean by Delegates.observable(false) { _, _, _ -> notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = UsageHistoryViewHolder(
            FragmentUsageHistoryItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            )
    )

    override fun onBindViewHolder(holder: UsageHistoryViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        val context = binding.root.context

        val timeAreaString = if (item == null || item.startMinuteOfDay == MinuteOfDay.MIN && item.endMinuteOfDay == MinuteOfDay.MAX)
            null
        else
            context.getString(R.string.usage_history_time_area, MinuteOfDay.format(item.startMinuteOfDay), MinuteOfDay.format(item.endMinuteOfDay))

        val dateStringPrefix = if (showCategoryTitle) item?.categoryTitle + " - " else ""

        if (item?.day != null) {
            val dateObject = LocalDate.ofEpochDay(item.day)
            val dateString = DateFormat.getDateFormat(context).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(dateObject.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000L))

            binding.date = dateStringPrefix + dateString
            binding.timeArea = timeAreaString
            binding.usedTime = TimeTextUtil.used(item.duration.toInt(), context)
        } else if (item?.lastUsage != null && item.maxSessionDuration != null && item.pauseDuration != null) {
            binding.date = dateStringPrefix + context.getString(
                    R.string.usage_history_item_session_duration_limit,
                    TimeTextUtil.time(item.maxSessionDuration.toInt(), context),
                    TimeTextUtil.time(item.pauseDuration.toInt(), context)
            )
            binding.timeArea = timeAreaString
            binding.usedTime = TimeTextUtil.used(item.duration.toInt(), context) + "\n" +
                    context.getString(
                            R.string.usage_history_item_last_usage,
                            DateUtils.formatDateTime(context, item.lastUsage, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE)
                    )
        } else {
            binding.date = ""
            binding.usedTime = ""
        }
    }
}

class UsageHistoryViewHolder(val binding: FragmentUsageHistoryItemBinding): RecyclerView.ViewHolder(binding.root)
