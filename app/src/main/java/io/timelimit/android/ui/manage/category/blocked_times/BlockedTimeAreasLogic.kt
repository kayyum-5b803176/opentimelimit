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
package io.timelimit.android.ui.manage.category.blocked_times

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.customtypes.ImmutableBitmask
import java.util.*

object BlockedTimeAreasLogic {
    fun init(
            recycler: RecyclerView,
            daySpinner: Spinner,
            detailedModeCheckbox: CheckBox,
            checkAuthentication: () -> Authentication,
            updateBlockedTimes: (ImmutableBitmask, ImmutableBitmask) -> Unit,
            currentData: LiveData<ImmutableBitmask?>,
            lifecycleOwner: LifecycleOwner
    ) {
        val context = recycler.context!!

        val items = MutableLiveData<BlockedTimeItems>().apply { value = FifteenMinutesOfWeekItems }
        val layoutManager = GridLayoutManager(context, items.value!!.recommendColumns)
        layoutManager.spanSizeLookup = SpanSizeLookup(items.value!!)

        val adapter = Adapter(items.value!!)

        items.observe(lifecycleOwner, Observer {
            layoutManager.spanCount = it.recommendColumns
            layoutManager.spanSizeLookup = SpanSizeLookup(it)
            adapter.items = it
        })

        recycler.adapter = adapter
        recycler.layoutManager = layoutManager

        adapter.handlers = object: Handlers {
            override fun onMinuteTileClick(time: MinuteTile) {
                val auth = checkAuthentication()

                if (auth is Authentication.Missing) {
                    auth.requestHook()

                    return
                }

                val selectedMinuteOfWeek = adapter.selectedMinuteOfWeek
                val blockedTimeAreas = adapter.blockedTimeAreas

                if (blockedTimeAreas == null) {
                    // nothing to work with
                } else if (selectedMinuteOfWeek == null) {
                    if (auth is Authentication.OnlyAllowAddingLimits) {
                        val start = time.minuteOfWeek
                        val end = start + time.lengthInMinutes

                        // if a fully blocked tile was selected
                        if (blockedTimeAreas.nextClearBit(start) >= end) {
                            auth.showErrorHook()

                            return
                        }

                        auth.showHintHook()
                    }

                    adapter.selectedMinuteOfWeek = time.minuteOfWeek
                } else if (selectedMinuteOfWeek == time.minuteOfWeek) {
                    adapter.selectedMinuteOfWeek = null

                    val newBlockMask = blockedTimeAreas.clone() as BitSet
                    newBlockMask.set(
                            selectedMinuteOfWeek,
                            selectedMinuteOfWeek + items.value!!.minutesPerTile,
                            auth is Authentication.OnlyAllowAddingLimits || !newBlockMask[selectedMinuteOfWeek]
                    )

                    updateBlockedTimes(ImmutableBitmask(blockedTimeAreas), ImmutableBitmask(newBlockMask))
                } else {
                    var times = selectedMinuteOfWeek to time.minuteOfWeek
                    adapter.selectedMinuteOfWeek = null

                    // sort selected times
                    if (times.first > times.second) {
                        times = times.second to times.first
                    }

                    // mark until the end
                    times = times.first to (times.second + items.value!!.minutesPerTile - 1)

                    // get majority of current value
                    var allowed = 0
                    var blocked = 0

                    for (i in times.first..times.second) {
                        if (blockedTimeAreas[i]) {
                            blocked++
                        } else {
                            allowed++
                        }
                    }

                    val isMajorityBlocked = blocked > allowed
                    val shouldBlock = auth is Authentication.OnlyAllowAddingLimits || !isMajorityBlocked

                    if (auth is Authentication.OnlyAllowAddingLimits && allowed == 0) {
                        auth.showErrorHook()

                        return
                    }

                    val newBlockMask = blockedTimeAreas.clone() as BitSet
                    newBlockMask.set(times.first, times.second + 1, shouldBlock)

                    updateBlockedTimes(ImmutableBitmask(blockedTimeAreas), ImmutableBitmask(newBlockMask))
                }
            }
        }

        run {
            val spinnerAdapter = ArrayAdapter.createFromResource(context, R.array.days_of_week_array, android.R.layout.simple_spinner_item)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            daySpinner.adapter = spinnerAdapter
            daySpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedDay = items.value!!.getDayOfPosition(
                            layoutManager.findFirstVisibleItemPosition()
                    )

                    if (selectedDay != position) {
                        layoutManager.scrollToPositionWithOffset(
                                items.value!!.getPositionOfItem(
                                        DayHeader(position)
                                ),
                                0
                        )
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // ignore
                }
            }
        }

        recycler.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    items.value?.let { items ->
                        try {
                            val selectedDay = items.getDayOfPosition(
                                    layoutManager.findFirstVisibleItemPosition()
                            )

                            if (selectedDay != daySpinner.selectedItemPosition) {
                                daySpinner.setSelection(selectedDay, true)
                            }
                        } catch (ex: IllegalStateException) {
                            // ignore
                        }
                    }
                }
            }
        })

        // bind detailed mode
        items.value = when (detailedModeCheckbox.isChecked) {
            true -> MinuteOfWeekItems
            false -> FifteenMinutesOfWeekItems
        }

        detailedModeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val oldValue = items.value
            val newValue = when (isChecked) {
                true -> MinuteOfWeekItems
                false -> FifteenMinutesOfWeekItems
            }

            if (oldValue != newValue) {
                val currentlyVisiblePosition = layoutManager.findFirstVisibleItemPosition()

                if (currentlyVisiblePosition == RecyclerView.NO_POSITION) {
                    items.value = newValue
                } else {
                    val currentlyVisibleItem = try { oldValue!!.getItemAtPosition(currentlyVisiblePosition) } catch (ex: IllegalStateException) { DayHeader(0) }
                    val newVisiblePosition = newValue.getPositionOfItem(currentlyVisibleItem)

                    items.value = newValue
                    layoutManager.scrollToPositionWithOffset(newVisiblePosition, 0)
                }
            }
        }

        // loading data
        currentData.observe(lifecycleOwner, Observer { adapter.blockedTimeAreas = it?.dataNotToModify })
    }

    sealed class Authentication {
        class Missing(val requestHook: () -> Unit): Authentication()
        object FullyAvailable: Authentication()
        class OnlyAllowAddingLimits(val showHintHook: () -> Unit, val showErrorHook: () -> Unit): Authentication()
    }
}