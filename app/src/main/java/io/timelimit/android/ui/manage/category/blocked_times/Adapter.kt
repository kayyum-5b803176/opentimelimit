/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentBlockedTimeAreasMinuteTileBinding
import io.timelimit.android.databinding.GenericBigListHeaderBinding
import io.timelimit.android.databinding.GenericListHeaderBinding
import java.util.*
import kotlin.properties.Delegates

class Adapter(items: BlockedTimeItems): RecyclerView.Adapter<ViewHolder>() {
    var blockedTimeAreas: BitSet? by Delegates.observable(null as BitSet?) { _, _, _ -> notifyDataSetChanged() }
    var selectedMinuteOfWeek: Int? by Delegates.observable(null as Int?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? by Delegates.observable(null as Handlers?) { _, _, _ -> notifyDataSetChanged() }
    var items: BlockedTimeItems by Delegates.observable(items) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)

        return when (item) {
            is DayHeader -> "day ${item.day}"
            is HourHeader -> "hour ${item.day}-${item.hour}"
            is MinuteTile -> "minute ${item.minuteOfWeek}"
        }.hashCode().toLong()
    }

    override fun getItemCount(): Int = items.itemsPerWeek

    private fun getItem(position: Int): Item = items.getItemAtPosition(position)

    private fun getViewType(item: Item): ViewType = when(item) {
        is DayHeader -> ViewType.Day
        is HourHeader -> ViewType.Hour
        is MinuteTile -> ViewType.Minute
    }

    override fun getItemViewType(position: Int): Int {
        return getViewType(getItem(position)).ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when(viewType) {
        ViewType.Day.ordinal ->
            DayHeaderViewHolder(
                    GenericBigListHeaderBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )
        ViewType.Hour.ordinal ->
            HourHeaderViewHolder(
                    GenericListHeaderBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )
        ViewType.Minute.ordinal -> MinuteTileViewHolder(
                FragmentBlockedTimeAreasMinuteTileBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
        )
        else -> throw IllegalStateException()
    }

    private fun timeToString(time: Int) = if (time == 0) {
        "00"
    } else if (time <= 9) {
        "0$time"
    } else {
        time.toString()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        when(item) {
            is DayHeader -> {
                if (holder !is DayHeaderViewHolder) {
                    throw IllegalStateException()
                }

                holder.view.text = holder.view.root.context.resources.getStringArray(R.array.days_of_week_array)[item.day]
            }
            is HourHeader -> {
                if (holder !is HourHeaderViewHolder) {
                    throw IllegalStateException()
                }

                holder.view.text = timeToString(item.hour) + ":00"
            }
            is MinuteTile -> {
                if (holder !is MinuteTileViewHolder) {
                    throw IllegalStateException()
                }

                holder.view.item = item
                holder.view.handlers = handlers
                holder.view.text = if (items.hasHourHeaders) {
                    timeToString(item.minute)
                } else {
                    timeToString(item.hour) + ":" + timeToString(item.minute)
                }

                val blockedTimeAreas = blockedTimeAreas

                if (blockedTimeAreas == null) {
                    holder.view.mode = MinuteTileMode.Allowed
                } else {
                    var setBits = 0
                    val maxBits = item.lengthInMinutes

                    for (i in 0..(maxBits-1)) {
                        if (blockedTimeAreas[item.minuteOfWeek + i]) {
                            setBits++
                        }
                    }

                    holder.view.mode = when (setBits) {
                        0 -> MinuteTileMode.Allowed
                        maxBits -> MinuteTileMode.Blocked
                        else -> MinuteTileMode.Mixed
                    }
                }

                holder.view.selected = selectedMinuteOfWeek == item.minuteOfWeek
            }
        }

        holder.baseView.executePendingBindings()
    }
}

class SpanSizeLookup(private val items: BlockedTimeItems): GridLayoutManager.SpanSizeLookup() {
    override fun getSpanSize(position: Int): Int {
        return try {
            if (items.getItemAtPosition(position) is MinuteTile) {
                1
            } else {
                items.recommendColumns
            }
        } catch (ex: IllegalStateException) {
            // element is out of bounds => don't care about the size
            // this case occurs only at some devices

            1
        }
    }
}

sealed class ViewHolder(val baseView: ViewDataBinding): RecyclerView.ViewHolder(baseView.root)
class DayHeaderViewHolder(val view: GenericBigListHeaderBinding): ViewHolder(view)
class HourHeaderViewHolder(val view: GenericListHeaderBinding): ViewHolder(view)
class MinuteTileViewHolder(val view: FragmentBlockedTimeAreasMinuteTileBinding): ViewHolder(view)

enum class ViewType {
    Day, Hour, Minute
}

interface Handlers {
    fun onMinuteTileClick(time: MinuteTile)
}
