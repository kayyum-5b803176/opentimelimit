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

package io.timelimit.android.ui.manage.child.tasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.ChildTaskItemBinding
import io.timelimit.android.databinding.IntroCardBinding
import io.timelimit.android.ui.util.DateUtil
import io.timelimit.android.util.TimeTextUtil
import kotlin.properties.Delegates

class ChildTaskAdapter: RecyclerView.Adapter<ChildTaskAdapter.Holder>() {
    companion object {
        private const val TYPE_ADD = 1
        private const val TYPE_INTRO = 2
        private const val TYPE_TASK = 3
    }

    var data: List<ChildTaskItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var listener: Listener? = null

    init { setHasStableIds(true) }

    override fun getItemCount(): Int = data.size

    override fun getItemId(position: Int): Long = data[position].let { item ->
        if (item is ChildTaskItem.Task) item.taskItem.taskId.hashCode() else item.hashCode()
    }.toLong()

    override fun getItemViewType(position: Int): Int = when (data[position]) {
        ChildTaskItem.Add -> TYPE_ADD
        ChildTaskItem.Intro -> TYPE_INTRO
        is ChildTaskItem.Task -> TYPE_TASK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = LayoutInflater.from(parent.context).let { inflater ->
        Holder(when (viewType) {
            TYPE_ADD -> AddItemViewBinding.inflate(inflater, parent, false).also {
                it.label = parent.context.getString(R.string.manage_child_tasks_add)
                it.root.setOnClickListener { listener?.onAddClicked() }
            }.root
            TYPE_INTRO -> IntroCardBinding.inflate(inflater, parent, false).also {
                it.title = parent.context.getString(R.string.manage_child_tasks)
                it.text = parent.context.getString(R.string.manage_child_tasks_intro)
            }.root
            TYPE_TASK -> ChildTaskItemBinding.inflate(inflater, parent, false).also {
                it.root.tag = it
            }.root
            else -> throw IllegalArgumentException()
        })
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = data[position]

        if (item is ChildTaskItem.Task) {
            val context = holder.itemView.context
            val binding = holder.itemView.tag as ChildTaskItemBinding

            binding.title = item.taskItem.taskTitle
            binding.category = item.categoryTitle
            binding.duration = TimeTextUtil.time(item.taskItem.extraTimeDuration, context)
            binding.lastGrant = item.taskItem.lastGrantTimestamp.let { time ->
                if (time == 0L) null else DateUtil.formatAbsoluteDate(context, time)
            }
            binding.pendingReview = item.taskItem.pendingRequest

            binding.executePendingBindings()

            binding.root.setOnClickListener { listener?.onTaskClicked(item.taskItem) }
        }
    }

    class Holder(view: View): RecyclerView.ViewHolder(view)

    interface Listener {
        fun onAddClicked()
        fun onTaskClicked(task: ChildTask)
    }
}