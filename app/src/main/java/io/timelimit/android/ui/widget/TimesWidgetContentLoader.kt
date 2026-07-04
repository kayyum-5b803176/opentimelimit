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
package io.timelimit.android.ui.widget

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache

object TimesWidgetContentLoader {
    fun with(logic: AppLogic): LiveData<TimesWidgetContent> {
        val database = logic.database
        val realTimeLogic = logic.realTimeLogic
        val timeApi = logic.timeApi
        val categoryHandlingCache = CategoryHandlingCache()
        val handler = Threads.mainThreadHandler

        val deviceAndUserRelatedDataLive = database.derivedDataDao().getUserAndDeviceRelatedDataLive()
        var deviceAndUserRelatedDataLiveLoaded = false

        val batteryStatusLive = logic.platformIntegration.getBatteryStatusLive()

        lateinit var timeModificationListener: () -> Unit
        lateinit var updateByClockRunnable: Runnable
        var isActive = false

        val newResult = object: MediatorLiveData<TimesWidgetContent>() {
            override fun onActive() {
                super.onActive()

                isActive = true

                realTimeLogic.registerTimeModificationListener(timeModificationListener)

                // ensure that the next update gets scheduled
                updateByClockRunnable.run()
            }

            override fun onInactive() {
                super.onInactive()

                isActive = true

                realTimeLogic.unregisterTimeModificationListener(timeModificationListener)
                handler.removeCallbacks(updateByClockRunnable)
            }
        }

        fun update() {
            handler.removeCallbacks(updateByClockRunnable)

            if (!deviceAndUserRelatedDataLiveLoaded) { return }

            val deviceAndUserRelatedData = deviceAndUserRelatedDataLive.value
            val userRelatedData = deviceAndUserRelatedData?.userRelatedData
            val canSwitchToDefaultUser = deviceAndUserRelatedData?.deviceRelatedData?.canSwitchToDefaultUser ?: false
            val timeInMillis = timeApi.getCurrentTimeInMillis()

            if (deviceAndUserRelatedData == null) {
                newResult.value = TimesWidgetContent.UnconfiguredDevice

                return
            } else if (userRelatedData?.user?.type != UserType.Child) {
                newResult.value = TimesWidgetContent.NoChildUser(
                    canSwitchToDefaultUser = canSwitchToDefaultUser
                )

                return
            }

            categoryHandlingCache.reportStatus(
                    user = userRelatedData,
                    timeInMillis = timeInMillis,
                    batteryStatus = logic.platformIntegration.getBatteryStatus(),
                    currentNetworkId = null // not relevant here
            )

            var maxTime = Long.MAX_VALUE

            val categories = userRelatedData.sortedCategories().map { (level, category) ->
                val handling = categoryHandlingCache.get(categoryId = category.category.id)

                maxTime = maxTime.coerceAtMost(handling.dependsOnMaxTime)

                TimesWidgetContent.Categories.Item(
                        categoryId = category.category.id,
                        categoryName = category.category.title,
                        level = level,
                        remainingTimeToday = handling.remainingTime?.includingExtraTime
                )
            }

            newResult.value = TimesWidgetContent.Categories(
                categories = categories,
                canSwitchToDefaultUser = canSwitchToDefaultUser
            )

            if (isActive && maxTime != Long.MAX_VALUE) {
                val delay = maxTime - timeInMillis

                handler.postDelayed(updateByClockRunnable, delay)
            }
        }

        timeModificationListener = { update() }
        updateByClockRunnable = Runnable { update() }

        newResult.addSource(deviceAndUserRelatedDataLive) { deviceAndUserRelatedDataLiveLoaded = true; update() }
        newResult.addSource(batteryStatusLive) { update() }

        return newResult.ignoreUnchanged()
    }
}