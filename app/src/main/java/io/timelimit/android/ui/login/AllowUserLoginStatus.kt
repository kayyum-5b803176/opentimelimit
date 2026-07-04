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

package io.timelimit.android.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.model.derived.CompleteUserLoginRelatedData
import io.timelimit.android.integration.platform.BatteryStatus
import io.timelimit.android.integration.platform.NetworkId
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.BlockingReason
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache
import java.lang.IllegalStateException
import java.util.concurrent.CountDownLatch

sealed class AllowUserLoginStatus {
    open val dependsOnNetworkId = false

    data class Allow(
        val maxTime: Long,
        override val dependsOnNetworkId: Boolean
    ): AllowUserLoginStatus()

    data class ForbidByCategory(
        val categoryTitle: String,
        val blockingReason: BlockingReason,
        val maxTime: Long,
        override val dependsOnNetworkId: Boolean
    ): AllowUserLoginStatus()

    object ForbidUserNotFound: AllowUserLoginStatus()
}

object AllowUserLoginStatusUtil {
    private fun calculate(
        data: CompleteUserLoginRelatedData,
        timeInMillis: Long,
        cache: CategoryHandlingCache,
        batteryStatus: BatteryStatus,
        currentNetworkId: NetworkId?
    ): AllowUserLoginStatus = synchronized(cache) {
        return if (data.limitLoginCategoryUserRelatedData != null && data.loginRelatedData.limitLoginCategory != null) {
            var currentCheckedTime = timeInMillis
            val preBlockDuration = data.loginRelatedData.limitLoginCategory.preBlockDuration
            val maxCheckedTime = timeInMillis + preBlockDuration
            val categoryIds = data.limitLoginCategoryUserRelatedData.getCategoryWithParentCategories(data.loginRelatedData.limitLoginCategory.categoryId)
            var dependsOnAnyNetworkId = false

            while (true) {
                cache.reportStatus(
                        user = data.limitLoginCategoryUserRelatedData,
                        timeInMillis = currentCheckedTime,
                        batteryStatus = batteryStatus,
                        currentNetworkId = currentNetworkId
                )

                val handlings = categoryIds.map { cache.get(it) }
                val remainingTimeToCheck = maxCheckedTime - currentCheckedTime

                dependsOnAnyNetworkId = dependsOnAnyNetworkId or (handlings.find { it.dependsOnNetworkId } != null)

                handlings.find { it.remainingSessionDuration != null && it.remainingSessionDuration < remainingTimeToCheck }?.let { blockingHandling ->
                    return AllowUserLoginStatus.ForbidByCategory(
                            categoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title,
                            blockingReason = BlockingReason.SessionDurationLimit,
                            maxTime = blockingHandling.dependsOnMaxTime,
                            dependsOnNetworkId = dependsOnAnyNetworkId
                    )
                }

                handlings.find { it.remainingTime != null && it.remainingTime.includingExtraTime < remainingTimeToCheck }?.let { blockingHandling ->
                    return AllowUserLoginStatus.ForbidByCategory(
                            categoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title,
                            blockingReason = BlockingReason.TimeOver,
                            maxTime = blockingHandling.dependsOnMaxTime,
                            dependsOnNetworkId = dependsOnAnyNetworkId
                    )
                }

                handlings.find { it.shouldBlockAtSystemLevel }?.let { blockingHandling ->
                    return AllowUserLoginStatus.ForbidByCategory(
                            categoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title,
                            blockingReason = blockingHandling.systemLevelBlockingReason,
                            maxTime = blockingHandling.dependsOnMaxTime,
                            dependsOnNetworkId = dependsOnAnyNetworkId
                    )
                }

                handlings.find { !it.okByNetworkId }?.let { blockingHandling ->
                    if (!dependsOnAnyNetworkId) throw IllegalStateException()

                    return AllowUserLoginStatus.ForbidByCategory(
                        categoryTitle = blockingHandling.createdWithCategoryRelatedData.category.title,
                        blockingReason = blockingHandling.activityBlockingReason,
                        maxTime = Long.MAX_VALUE,
                        dependsOnNetworkId = dependsOnAnyNetworkId
                    )
                }

                if (currentCheckedTime >= maxCheckedTime) break

                val maxTimeByCategories = categoryIds.map { cache.get(it) }.minByOrNull { it.dependsOnMaxTime }?.dependsOnMaxTime ?: Long.MAX_VALUE

                currentCheckedTime = maxTimeByCategories.coerceAtLeast(currentCheckedTime + 100).coerceAtMost(maxCheckedTime)
            }

            val maxTimeByCategories = categoryIds.map { cache.get(it) }.minByOrNull { it.dependsOnMaxTime }?.dependsOnMaxTime ?: Long.MAX_VALUE

            AllowUserLoginStatus.Allow(
                    maxTime = (maxTimeByCategories - preBlockDuration).coerceAtLeast(timeInMillis + 1000),
                    dependsOnNetworkId = dependsOnAnyNetworkId
            )
        } else {
            AllowUserLoginStatus.Allow(
                    maxTime = Long.MAX_VALUE,
                    dependsOnNetworkId = false
            )
        }
    }

    fun calculateSync(logic: AppLogic, userId: String): AllowUserLoginStatus {
        val userRelatedData = logic.database.derivedDataDao().getUserLoginRelatedDataSync(userId) ?: return AllowUserLoginStatus.ForbidUserNotFound
        val timeInMillis = logic.timeApi.getCurrentTimeInMillis()
        val batteryStatus = logic.platformIntegration.getBatteryStatus()

        val attempt1 = calculate(
            data = userRelatedData,
            batteryStatus = batteryStatus,
            timeInMillis = timeInMillis,
            cache = CategoryHandlingCache(),
            currentNetworkId = null
        )

        return if (attempt1.dependsOnNetworkId) {
            val currentNetworkId = CountDownLatch(1).let { latch ->
                var currentNetworkId: NetworkId? = null

                Threads.mainThreadHandler.post {
                    currentNetworkId = logic.platformIntegration.getCurrentNetworkId()
                    latch.countDown()
                }

                latch.await()

                currentNetworkId!!
            }

            calculate(
                data = userRelatedData,
                batteryStatus = batteryStatus,
                timeInMillis = timeInMillis,
                cache = CategoryHandlingCache(),
                currentNetworkId = currentNetworkId
            )
        } else attempt1
    }

    fun calculateLive(logic: AppLogic, userId: String): LiveData<AllowUserLoginStatus> = object : MediatorLiveData<AllowUserLoginStatus>() {
        val cache = CategoryHandlingCache()
        val currentNetworkIdLive = liveDataFromFunction { logic.platformIntegration.getCurrentNetworkId() }.ignoreUnchanged()
        var batteryStatus: BatteryStatus? = null
        var hasUserLoginRelatedData = false
        var userLoginRelatedData: CompleteUserLoginRelatedData? = null
        var isObservingNetworkId = false
        var currentNetworkId: NetworkId? = null

        init {
            addSource(logic.platformIntegration.getBatteryStatusLive(), androidx.lifecycle.Observer {
                batteryStatus = it; update()
            })

            addSource(logic.database.derivedDataDao().getUserLoginRelatedDataLive(userId), androidx.lifecycle.Observer {
                userLoginRelatedData = it; hasUserLoginRelatedData = true; update()
            })
        }

        val updateLambda: () -> Unit = { update() }
        val updateRunnable = Runnable { update() }

        fun update() {
            val batteryStatus = batteryStatus
            val userLoginRelatedData = userLoginRelatedData

            if (batteryStatus == null || !hasUserLoginRelatedData) return

            if (userLoginRelatedData == null) {
                if (value !== AllowUserLoginStatus.ForbidUserNotFound) {
                    value = AllowUserLoginStatus.ForbidUserNotFound
                }

                return
            }

            val timeInMillis = logic.timeApi.getCurrentTimeInMillis()

            val result = calculate(
                    data = userLoginRelatedData,
                    batteryStatus = batteryStatus,
                    cache = cache,
                    timeInMillis = timeInMillis,
                    currentNetworkId = currentNetworkId
            )

            if (result != value) {
                value = result
            }

            val scheduledTime: Long = when (result) {
                AllowUserLoginStatus.ForbidUserNotFound -> Long.MAX_VALUE
                is AllowUserLoginStatus.Allow -> result.maxTime
                is AllowUserLoginStatus.ForbidByCategory -> result.maxTime
            }

            if (scheduledTime != Long.MAX_VALUE) {
                logic.timeApi.cancelScheduledAction(updateRunnable)
                logic.timeApi.runDelayedByUptime(updateRunnable, scheduledTime - timeInMillis)
            }


            if (result.dependsOnNetworkId != isObservingNetworkId) {
                // important detail: the addSource can call update immediately
                isObservingNetworkId = result.dependsOnNetworkId

                if (result.dependsOnNetworkId) {
                    addSource(currentNetworkIdLive) {
                        currentNetworkId = it; update()
                    }
                } else {
                    removeSource(currentNetworkIdLive)
                    currentNetworkId = null
                }
            }
        }

        override fun onActive() {
            super.onActive()

            logic.realTimeLogic.registerTimeModificationListener(updateLambda)

            update()
        }

        override fun onInactive() {
            super.onInactive()

            logic.realTimeLogic.unregisterTimeModificationListener(updateLambda)
            logic.timeApi.cancelScheduledAction(updateRunnable)
        }
    }
}