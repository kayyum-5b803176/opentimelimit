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

package io.timelimit.android.ui.manage.category.appsandrules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.R
import io.timelimit.android.data.extensions.getDateLive
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.extensions.takeDistributedElements
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.DummyApps
import java.util.*

class AppsAndRulesModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database
    private val userIdLive = MutableLiveData<String>()
    private val categoryIdLive = MutableLiveData<String>()
    private val showAllAppsLive = MutableLiveData<Boolean>().apply { value = false }
    private val showAllRulesLive = MutableLiveData<Boolean>().apply { value = false }
    private var didInit = false

    private val userDate = userIdLive.switchMap { userId ->
        database.user().getUserByIdLive(userId).getDateLive(logic.timeApi)
    }

    private val userDayOfWeek = userDate.map { it.dayOfWeek }

    val dateAndUsedTimes = userDate.switchMap { date ->
        categoryIdLive.switchMap { categoryId ->
            database.usedTimes().getUsedTimesOfWeek(
                    categoryId = categoryId,
                    firstDayOfWeekAsEpochDay = date.firstDayOfWeekAsEpochDay
            )
        }.map { usedTimes ->
            date to usedTimes
        }
    }

    private val allRules = categoryIdLive.switchMap { categoryId ->
        database.timeLimitRules().getTimeLimitRulesByCategory(categoryId)
    }.map { rules ->
        rules.sortedWith { a, b ->
            if (a.appliesToMultipleDays != b.appliesToMultipleDays) {
                if (a.appliesToMultipleDays) -1 else 1
            } else {
                if (a.dayMask < b.dayMask) -1
                else if (a.dayMask > b.dayMask) 1 else {
                    if (a.appliesToWholeDay != b.appliesToWholeDay) {
                        if (a.appliesToWholeDay) -1 else 1
                    } else {
                        0
                    }
                }
            }
        }.map { AppAndRuleItem.RuleEntry(it) }
    }

    private val todayRulesAndHasHiddenItems = allRules.switchMap { allRules ->
        userDayOfWeek.map { userDayOfWeek ->
            val newList = allRules.filter { it.rule.dayMask.toInt() and (1 shl userDayOfWeek) != 0 }

            newList to (newList.size != allRules.size)
        }
    }

    private val visibleRuleItems = showAllRulesLive.switchMap { showAllRules ->
        if (showAllRules)
            allRules.map { allRules -> allRules + listOf(AppAndRuleItem.AddRuleItem) }
        else
            todayRulesAndHasHiddenItems.map { (rules, hasHiddenItems) ->
                if (hasHiddenItems)
                    rules + listOf(AppAndRuleItem.ExpandRulesItem)
                else
                    rules + listOf(AppAndRuleItem.AddRuleItem)
            }
    }

    private val hasHiddenRuleIntro = database.config().wereHintsShown(HintsToShow.TIME_LIMIT_RULE_INTRODUCTION)

    private val fullRuleScreenContent = visibleRuleItems.switchMap { visibleRuleItems ->
        hasHiddenRuleIntro.map { hasHiddenRuleIntro ->
            if (hasHiddenRuleIntro) {
                visibleRuleItems
            } else {
                listOf(AppAndRuleItem.RulesIntro) + visibleRuleItems
            }
        }
    }

    private val installedApps = database.app().getApps()

    private val installedAppsIndexed = installedApps.map { apps -> apps.associateBy { it.packageName } }

    private val appsOfThisCategory = categoryIdLive.switchMap { categoryId -> database.categoryApp().getCategoryApps(categoryId) }

    private val appsOfCategoryWithNames = mergeLiveDataWaitForValues(installedAppsIndexed, appsOfThisCategory)
        .map { (allAppsIndexed, appsOfThisCategory) ->
            appsOfThisCategory.map { categoryApp ->
                val title = DummyApps.getTitle(categoryApp.appSpecifier.packageName, getApplication()) ?:
                allAppsIndexed[categoryApp.appSpecifier.packageName]?.title

                AppAndRuleItem.AppEntry(
                    title = title ?: "app not found",
                    specifier = categoryApp.appSpecifier
                )
            }
        }

    private val appEntries = appsOfCategoryWithNames.map { apps ->
        apps.sortedBy { it.title.lowercase() }
    }

    private val fullAppScreenContent = showAllAppsLive.switchMap { showAllApps ->
        if (showAllApps)
            appEntries.map { it + listOf(AppAndRuleItem.AddAppItem) }
        else
            appEntries.map { entries ->
                val maxSize = 3

                if (entries.size > maxSize)
                    entries.takeDistributedElements(maxSize) + listOf(
                        AppAndRuleItem.ExpandAppsItem, AppAndRuleItem.AddAppItem
                    )
                else
                    entries + listOf(AppAndRuleItem.AddAppItem)
            }
    }

    val combinedList = fullAppScreenContent.switchMap { apps ->
        fullRuleScreenContent.map { rules ->
            listOf(AppAndRuleItem.Headline(R.string.category_apps_title)) + apps +
                    listOf(AppAndRuleItem.Headline(R.string.category_time_limit_rules)) + rules
        }
    }

    fun init(userId: String, categoryId: String) {
        if (didInit) return; didInit = true

        userIdLive.value = userId
        categoryIdLive.value = categoryId
    }

    fun showAllApps() { showAllAppsLive.value = true }
    fun showAllRules() { showAllRulesLive.value = true }
}