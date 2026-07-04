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

package io.timelimit.android.ui.manage.child.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.databinding.RecyclerFragmentBinding
import io.timelimit.android.sync.actions.UpdateChildTaskAction
import io.timelimit.android.ui.main.getActivityViewModel

class ManageChildTasksFragment: Fragment(), EditTaskDialogFragment.Listener {
    companion object {
        private const val CHILD_ID = "childId"

        fun newInstance(childId: String) = ManageChildTasksFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
            }
        }
    }

    private val childId get() = requireArguments().getString(CHILD_ID)!!
    private val auth get() = getActivityViewModel(requireActivity())
    private val model: ChildTaskModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model.init(childId = childId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = RecyclerFragmentBinding.inflate(inflater, container, false)

        val adapter = ChildTaskAdapter()

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        model.listContent.observe(viewLifecycleOwner) { adapter.data = it }
        model.isChildTheCurrentDeviceUser.observe(viewLifecycleOwner) {/* keep the value fresh */}

        adapter.listener = object: ChildTaskAdapter.Listener {
            override fun onAddClicked() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    EditTaskDialogFragment.newInstance(childId = childId, taskId = null, listener = this@ManageChildTasksFragment).show(parentFragmentManager)
                }
            }

            override fun onTaskClicked(task: ChildTask) {
                if (auth.isParentAuthenticated()) {
                    EditTaskDialogFragment.newInstance(childId = childId, taskId = task.taskId, listener = this@ManageChildTasksFragment).show(parentFragmentManager)
                } else if (model.isChildTheCurrentDeviceUser.value == true && !task.pendingRequest) {
                    ConfirmTaskDialogFragment.newInstance(
                            taskId = task.taskId,
                            taskTitle = task.taskTitle,
                            fromManageScreen = true
                    ).show(parentFragmentManager)
                } else auth.requestAuthentication()
            }
        }

        ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(0, 0) {
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val index = viewHolder.adapterPosition
                val item = if (index == RecyclerView.NO_POSITION) null else adapter.data[index]

                return if (item == ChildTaskItem.Intro) {
                    ItemTouchHelper.START or ItemTouchHelper.END
                } else 0
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { model.hideIntro() }
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = throw IllegalStateException()
        }).attachToRecyclerView(binding.recycler)

        return binding.root
    }

    override fun onTaskRemoved(task: ChildTask) {
        Snackbar.make(requireView(), R.string.manage_child_tasks_toast_removed, Snackbar.LENGTH_SHORT)
                .setAction(R.string.generic_undo) {
                    auth.tryDispatchParentAction(
                            UpdateChildTaskAction(
                                    isNew = true,
                                    taskId = task.taskId,
                                    taskTitle = task.taskTitle,
                                    extraTimeDuration = task.extraTimeDuration,
                                    categoryId = task.categoryId
                            )
                    )
                }
                .show()
    }

    override fun onTaskSaved() {
        Snackbar.make(requireView(), R.string.manage_child_tasks_toast_saved, Snackbar.LENGTH_SHORT).show()
    }
}