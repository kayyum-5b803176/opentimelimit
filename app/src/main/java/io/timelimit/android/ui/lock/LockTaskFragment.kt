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

package io.timelimit.android.ui.lock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.databinding.RecyclerFragmentBinding
import io.timelimit.android.ui.manage.child.tasks.ConfirmTaskDialogFragment

class LockTaskFragment: Fragment() {
    private val model: LockModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = RecyclerFragmentBinding.inflate(inflater, container, false)

        val adapter = LockTaskAdapter()

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        model.blockedCategoryTasks.observe(viewLifecycleOwner) { tasks ->
            adapter.content = listOf(LockTaskItem.Introduction) + tasks.map { LockTaskItem.Task(it) }
        }

        adapter.listener = object: LockTaskAdapter.Listener {
            override fun onTaskClicked(task: ChildTask) {
                if (task.pendingRequest)
                    TaskReviewPendingDialogFragment.newInstance().show(parentFragmentManager)
                else
                    ConfirmTaskDialogFragment.newInstance(taskId = task.taskId, taskTitle = task.taskTitle, fromManageScreen = false).show(parentFragmentManager)
            }
        }

        return binding.root
    }
}