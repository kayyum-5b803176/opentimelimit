/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.Database
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.withConfigCopiedToOtherDates
import io.timelimit.android.databinding.FragmentBlockedTimeAreasBinding
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryBlockedTimesAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.mustread.MustReadFragment

class BlockedTimeAreasFragment : Fragment(), CopyBlockedTimeAreasDialogFragmentListener {
    companion object {
        private const val CHILD_ID = "childId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(childId: String, categoryId: String) = BlockedTimeAreasFragment().apply {
            arguments = Bundle().apply {
                putString(CHILD_ID, childId)
                putString(CATEGORY_ID, categoryId)
            }
        }
    }

    private val database: Database by lazy { DefaultAppLogic.with(requireContext()).database }
    private val category: LiveData<Category?> by lazy { database.category().getCategoryByChildIdAndId(childId, categoryId) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val items = MutableLiveData<BlockedTimeItems>()
    private val childId: String get() = requireArguments().getString(CHILD_ID)!!
    private val categoryId: String get() = requireArguments().getString(CATEGORY_ID)!!
    private lateinit var binding: FragmentBlockedTimeAreasBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        items.value = MinuteOfWeekItems

        if (savedInstanceState == null) {
            database.let { database ->
                runAsync {
                    val wasShown = database.config().wereHintsShown(HintsToShow.BLOCKED_TIME_AREAS_OBSOLETE).waitForNonNullValue()

                    if (!wasShown) {
                        MustReadFragment.newInstance(R.string.must_read_blocked_time_areas_obsolete).show(parentFragmentManager)

                        Threads.database.execute {
                            database.config().setHintsShownSync(HintsToShow.BLOCKED_TIME_AREAS_OBSOLETE)
                        }
                    }
                }
            }
        }
    }

    fun updateBlockedTimes(oldMask: ImmutableBitmask, newMask: ImmutableBitmask) {
        if (
                auth.tryDispatchParentAction(
                        action = UpdateCategoryBlockedTimesAction(
                                categoryId = categoryId,
                                blockedTimes = newMask
                        ),
                        allowAsChild = true
                )
        ) {
            Snackbar.make(binding.coordinator.parent as View, R.string.blocked_time_areas_snackbar_modified, Snackbar.LENGTH_SHORT)
                    .also {
                        if (auth.isParentAuthenticated()) {
                            it.setAction(R.string.generic_undo) {
                                auth.tryDispatchParentAction(
                                        UpdateCategoryBlockedTimesAction(
                                                categoryId = categoryId,
                                                blockedTimes = oldMask
                                        )
                                )
                            }
                        }
                    }
                    .show()
        }
    }

    override fun onCopyBlockedTimeAreasConfirmed(sourceDay: Int, targetDays: Set<Int>) {
        category.value?.blockedMinutesInWeek?.let { current ->
            updateBlockedTimes(current, current.withConfigCopiedToOtherDates(sourceDay, targetDays))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBlockedTimeAreasBinding.inflate(inflater, container, false)

        binding.btnHelp.setOnClickListener {
            BlockedTimeAreasHelpDialog().show(parentFragmentManager)
        }

        binding.btnCopyToOtherDays.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                CopyBlockedTimeAreasDialogFragment.newInstance(this@BlockedTimeAreasFragment).show(parentFragmentManager)
            }
        }

        BlockedTimeAreasLogic.init(
                recycler = binding.recycler,
                daySpinner = binding.spinnerDay,
                detailedModeCheckbox = binding.detailedMode,
                checkAuthentication = {
                    if (auth.isParentAuthenticated()) {
                        BlockedTimeAreasLogic.Authentication.FullyAvailable
                    } else if (auth.isParentOrChildAuthenticated(childId = childId)) {
                        BlockedTimeAreasLogic.Authentication.OnlyAllowAddingLimits(
                                showHintHook = { Snackbar.make(binding.coordinator.parent as View, R.string.blocked_time_areas_snackbar_child_hint, Snackbar.LENGTH_LONG).show() },
                                showErrorHook = { auth.requestAuthentication() }
                        )
                    } else {
                        BlockedTimeAreasLogic.Authentication.Missing(
                                requestHook = { auth.requestAuthentication() }
                        )
                    }
                },
                updateBlockedTimes = { a, b -> updateBlockedTimes(a, b) },
                currentData = category.map { it?.blockedMinutesInWeek },
                lifecycleOwner = this
        )

        return binding.root
    }
}
