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

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.databinding.EditTaskFragmentBinding
import io.timelimit.android.extensions.addOnTextChangedListener
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.util.bind

class EditTaskDialogFragment: BottomSheetDialogFragment(), EditTaskCategoryDialogFragment.Listener {
    companion object {
        private const val DIALOG_TAG = "EditTaskDialogFragment"

        private const val CHILD_ID = "childId"
        private const val TASK_ID = "taskId"

        fun newInstance(childId: String, taskId: String?, listener: Fragment) = EditTaskDialogFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                if (taskId != null) putString(TASK_ID, taskId)

                setTargetFragment(listener, 0)
            }
        }
    }

    private val auth get() = getActivityViewModel(requireActivity())
    private val model by viewModels<EditTaskModel>()
    private val target get() = targetFragment as Listener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = EditTaskFragmentBinding.inflate(inflater, container, false)
        val args = requireArguments()
        val childId = args.getString(CHILD_ID)!!
        val taskId = if (args.containsKey(TASK_ID)) args.getString(TASK_ID) else null

        model.init(childId = childId, taskId = taskId)

        binding.isNewTask = taskId == null

        binding.taskTitle.filters = arrayOf(InputFilter.LengthFilter(ChildTask.MAX_TASK_TITLE_LENGTH))

        binding.taskTitle.addOnTextChangedListener {
            val value = binding.taskTitle.text.toString()

            if (model.taskTitleLive.value != value) model.taskTitleLive.value = value
        }

        model.taskTitleLive.observe(viewLifecycleOwner) { value ->
            if (value != binding.taskTitle.text.toString()) binding.taskTitle.setText(value)
        }

        model.selectedCategoryTitle.observe(viewLifecycleOwner) { categoryTitle ->
            binding.categoryDropdown.text = categoryTitle ?: getString(R.string.manage_child_tasks_select_category)
        }

        binding.categoryDropdown.setOnClickListener {
            EditTaskCategoryDialogFragment.newInstance(childId = childId, categoryId = model.categoryIdLive.value, target = this).show(parentFragmentManager)
        }

        binding.timespan.bind(model.logic.database, viewLifecycleOwner) {
            if (model.durationLive.value != it) model.durationLive.value = it
        }

        model.durationLive.observe(viewLifecycleOwner) {
            if (it != binding.timespan.timeInMillis) binding.timespan.timeInMillis = it
        }

        binding.confirmButton.isEnabled = false
        model.valid.observe(viewLifecycleOwner) { binding.confirmButton.isEnabled = it }

        model.shouldClose.observe(viewLifecycleOwner) { if (it) dismissAllowingStateLoss() }
        model.isBusy.observe(viewLifecycleOwner) { binding.flipper.displayedChild = if (it) 1 else 0 }

        binding.deleteButton.setOnClickListener { model.deleteRule(auth) { target.onTaskRemoved(it) } }
        binding.confirmButton.setOnClickListener { model.saveRule(auth); target.onTaskSaved() }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth.authenticatedUser.observe(this) {
            if (it == null) dismissAllowingStateLoss()
        }
    }

    override fun onCategorySelected(categoryId: String) { model.categoryIdLive.value = categoryId }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, DIALOG_TAG)

    interface Listener {
        fun onTaskRemoved(task: ChildTask)
        fun onTaskSaved()
    }
}