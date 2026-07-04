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

package io.timelimit.android.ui.lock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.databinding.ChildTaskItemBinding
import io.timelimit.android.databinding.IntroCardBinding
import io.timelimit.android.util.TimeTextUtil
import kotlin.properties.Delegates

class LockTaskAdapter: RecyclerView.Adapter<LockTaskAdapter.Holder>() {
    companion object {
        private const val TYPE_INTRODUCTION = 1
        private const val TYPE_ITEM = 2
    }

    var content: List<LockTaskItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var listener: Listener? = null

    init { setHasStableIds(true) }

    override fun getItemCount(): Int = content.size

    override fun getItemId(position: Int): Long = content[position].let { item ->
        when (item) {
            is LockTaskItem.Task -> item.task.taskId.hashCode()
            else -> item.hashCode()
        }
    }.toLong()

    override fun getItemViewType(position: Int): Int = when (content[position]) {
        is LockTaskItem.Task -> TYPE_ITEM
        LockTaskItem.Introduction -> TYPE_INTRODUCTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = LayoutInflater.from(parent.context).let { inflater ->
        LockTaskAdapter.Holder(
                when (viewType) {
                    TYPE_INTRODUCTION -> IntroCardBinding.inflate(inflater, parent, false).also {
                        it.title = parent.context.getString(R.string.lock_tab_task)
                        it.text = parent.context.getString(R.string.lock_task_introduction)
                        it.noSwipe = true
                    }.root
                    TYPE_ITEM -> ChildTaskItemBinding.inflate(inflater, parent, false).also {
                        it.root.tag = it
                    }.root
                    else -> throw IllegalArgumentException()
                }
        )
    }

    override fun onBindViewHolder(holder: LockTaskAdapter.Holder, position: Int) {
        val context = holder.itemView.context
        val item = content[position]

        when (item) {
            LockTaskItem.Introduction -> {/* nothing to do */}
            is LockTaskItem.Task -> {
                val binding = holder.itemView.tag as ChildTaskItemBinding

                binding.title = item.task.taskTitle
                binding.duration = TimeTextUtil.time(item.task.extraTimeDuration, context)
                binding.pendingReview = item.task.pendingRequest

                binding.executePendingBindings()

                binding.root.setOnClickListener { listener?.onTaskClicked(item.task) }
            }
        }.let {/* require handling all paths */}
    }

    class Holder(view: View): RecyclerView.ViewHolder(view)

    interface Listener {
        fun onTaskClicked(task: ChildTask)
    }
}