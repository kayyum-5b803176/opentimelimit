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

package io.timelimit.android.logic.blockingreason

import io.timelimit.android.BuildConfig
import io.timelimit.android.data.extensions.getCategoryWithParentCategories
import io.timelimit.android.data.model.derived.DeviceRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.integration.platform.android.AndroidIntegrationApps
import io.timelimit.android.logic.BlockingLevel
import io.timelimit.android.logic.DummyApps

sealed class AppBaseHandling {
    abstract val level: BlockingLevel
    abstract val needsNetworkId: Boolean
    abstract fun getCategories(purpose: GetCategoriesPurpose): Iterable<String>

    object Idle: AppBaseHandling() {
        override val level = BlockingLevel.App
        override val needsNetworkId = false
        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = emptyList()
    }

    object PauseLogic: AppBaseHandling() {
        override val level = BlockingLevel.App
        override val needsNetworkId = false
        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = emptyList()
    }

    sealed class Whitelist: AppBaseHandling() {
        override val needsNetworkId = false
        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = emptyList()

        object App: Whitelist() {
            override val level = BlockingLevel.App
        }

        object Activity: Whitelist() {
            override val level = BlockingLevel.Activity
        }
    }

    object TemporarilyAllowed: AppBaseHandling() {
        override val level = BlockingLevel.App
        override val needsNetworkId = false
        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = emptyList()
    }

    object BlockDueToNoCategory: AppBaseHandling() {
        override val level = BlockingLevel.App
        override val needsNetworkId = false
        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = emptyList()
    }

    data class SanctionCountEverything(val categoryIds: Set<String>): AppBaseHandling() {
        override val level = BlockingLevel.App
        override val needsNetworkId = false

        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = when (purpose) {
            GetCategoriesPurpose.ShowingInStatusNotification -> emptyList()
            GetCategoriesPurpose.DelayedSessionDurationCounting -> categoryIds
            GetCategoriesPurpose.UsageCounting -> categoryIds
            GetCategoriesPurpose.Blocking -> emptyList()
        }
    }

    data class UseCategories(
            val categoryIds: Set<String>,
            val shouldCount: Boolean,
            override val level: BlockingLevel,
            override val needsNetworkId: Boolean
    ): AppBaseHandling() {
        init {
            if (categoryIds.isEmpty()) {
                throw IllegalStateException()
            }
        }

        override fun getCategories(purpose: GetCategoriesPurpose): Iterable<String> = when (purpose) {
            GetCategoriesPurpose.ShowingInStatusNotification -> categoryIds
            GetCategoriesPurpose.DelayedSessionDurationCounting -> categoryIds
            GetCategoriesPurpose.UsageCounting -> if (shouldCount) categoryIds else emptyList()
            GetCategoriesPurpose.Blocking -> categoryIds
        }
    }

    companion object {
        fun calculate(
                foregroundAppPackageName: String?,
                foregroundAppActivityName: String?,
                pauseForegroundAppBackgroundLoop: Boolean,
                pauseCounting: Boolean,
                userRelatedData: UserRelatedData,
                deviceRelatedData: DeviceRelatedData,
                isSystemImageApp: Boolean
        ): AppBaseHandling {
            if (pauseForegroundAppBackgroundLoop) {
                return PauseLogic
            } else if (
                    (foregroundAppPackageName == BuildConfig.APPLICATION_ID) ||
                    (foregroundAppPackageName != null && isSystemImageApp && AndroidIntegrationApps.ignoredApps.contains(foregroundAppPackageName))
            ) {
                return Whitelist.App
            } else if (
                foregroundAppPackageName != null && foregroundAppActivityName != null &&
                isSystemImageApp && AndroidIntegrationApps.shouldIgnoreActivity(foregroundAppPackageName, foregroundAppActivityName)
            ) {
                return Whitelist.Activity
            } else if (foregroundAppPackageName != null && deviceRelatedData.temporarilyAllowedApps.contains(foregroundAppPackageName)) {
                return TemporarilyAllowed
            } else if (foregroundAppPackageName != null) {
                val appCategory = run {
                    val tryActivityLevelBlocking = deviceRelatedData.deviceEntry.enableActivityLevelBlocking && foregroundAppActivityName != null
                    val appLevelCategory = userRelatedData.findCategoryAppByPackageAndActivityName(foregroundAppPackageName, null) ?: run {
                        if (isSystemImageApp) userRelatedData.findCategoryAppByPackageAndActivityName(DummyApps.NOT_ASSIGNED_SYSTEM_IMAGE_APP, null) else null
                    }

                    (if (tryActivityLevelBlocking) {
                        userRelatedData.findCategoryAppByPackageAndActivityName(foregroundAppPackageName, foregroundAppActivityName)
                    } else {
                        null
                    }) ?: appLevelCategory
                }

                val startCategory = userRelatedData.categoryById[appCategory?.categoryId]
                        ?: userRelatedData.categoryById[userRelatedData.user.categoryForNotAssignedApps]

                if (startCategory == null) {
                    return BlockDueToNoCategory
                } else {
                    val categoryIds = userRelatedData.getCategoryWithParentCategories(startCategoryId = startCategory.category.id)

                    return UseCategories(
                            categoryIds = categoryIds,
                            shouldCount = !pauseCounting,
                            level = when (appCategory == null || appCategory.appSpecifier.activityName != null) {
                                true -> BlockingLevel.Activity
                                false -> BlockingLevel.App
                            },
                            needsNetworkId = categoryIds.find { categoryId ->
                                userRelatedData.categoryById[categoryId]!!.networks.isNotEmpty()
                            } != null
                    )
                }
            } else {
                return Idle
            }
        }

        fun getCategories(items: List<AppBaseHandling>, purpose: GetCategoriesPurpose): Set<String> {
            val result = mutableSetOf<String>()

            items.forEach { item ->
                result.addAll(item.getCategories(purpose))
            }

            return result
        }
    }

    enum class GetCategoriesPurpose {
        ShowingInStatusNotification,
        DelayedSessionDurationCounting,
        UsageCounting,
        Blocking
    }
}