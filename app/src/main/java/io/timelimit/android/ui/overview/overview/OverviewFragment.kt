/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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

package io.timelimit.android.ui.overview.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.CoroutineFragment
import io.timelimit.android.data.model.*
import io.timelimit.android.databinding.FragmentOverviewBinding
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ReviewChildTaskAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import java.util.*

class OverviewFragment : CoroutineFragment() {
    private val handlers: OverviewFragmentParentHandlers by lazy { parentFragment as OverviewFragmentParentHandlers }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val model: OverviewFragmentModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentOverviewBinding.inflate(inflater, container, false)

        val adapter = OverviewFragmentAdapter()

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter.handlers = object: OverviewFragmentHandlers {
            override fun onAddUserClicked() {
                handlers.openAddUserScreen()
            }

            override fun onDeviceClicked(device: Device) {
                handlers.openManageDeviceScreen(deviceId = device.id)
            }

            override fun onUserClicked(user: User) {
                if (
                        user.restrictViewingToParents &&
                        logic.deviceUserId.value != user.id &&
                        !auth.requestAuthenticationOrReturnTrue()
                ) {
                    // do "nothing"/ request authentication
                } else {
                    when (user.type) {
                        UserType.Child -> handlers.openManageChildScreen(childId = user.id)
                        UserType.Parent -> handlers.openManageParentScreen(parentId = user.id)
                    }.let { }
                }
            }

            override fun onShowAllUsersClicked() {
                model.showAllUsers()
            }

            override fun onTaskConfirmed(task: ChildTask, timezone: TimeZone) {
                val time = logic.timeApi.getCurrentTimeInMillis()
                val day = DateInTimezone.newInstance(time, timezone).dayOfEpoch

                auth.tryDispatchParentAction(
                        ReviewChildTaskAction(
                                taskId = task.taskId,
                                ok = true,
                                time = time,
                                day = day
                        )
                )
            }

            override fun onTaskRejected(task: ChildTask) {
                auth.tryDispatchParentAction(
                        ReviewChildTaskAction(
                                taskId = task.taskId,
                                ok = false,
                                time = logic.timeApi.getCurrentTimeInMillis(),
                                day = null
                        )
                )
            }

            override fun onSkipTaskReviewClicked(task: ChildTask) {
                if (auth.requestAuthenticationOrReturnTrue()) model.hideTask(task.taskId)
            }
        }

        model.listEntries.observe(viewLifecycleOwner) { adapter.data = it }

        ItemTouchHelper(
                object: ItemTouchHelper.Callback() {
                    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                        val index = viewHolder.adapterPosition
                        val item = if (index == RecyclerView.NO_POSITION) null else adapter.data!![index]

                        if (item == OverviewFragmentHeaderIntro) {
                            return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.END or ItemTouchHelper.START) or
                                    makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END or ItemTouchHelper.START)
                        } else {
                            return 0
                        }
                    }

                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = throw IllegalStateException()

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        // remove the introduction header
                        Threads.database.execute {
                            logic.database.config().setHintsShownSync(HintsToShow.OVERVIEW_INTRODUCTION)
                        }
                    }
                }
        ).attachToRecyclerView(binding.recycler)

        return binding.root
    }
}

interface OverviewFragmentParentHandlers {
    fun openAddUserScreen()
    fun openManageDeviceScreen(deviceId: String)
    fun openManageChildScreen(childId: String)
    fun openManageParentScreen(parentId: String)
}
