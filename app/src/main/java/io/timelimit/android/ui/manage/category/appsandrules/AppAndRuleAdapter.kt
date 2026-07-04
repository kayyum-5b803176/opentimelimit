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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.databinding.*
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.DummyApps
import io.timelimit.android.logic.RemainingTime
import io.timelimit.android.ui.manage.category.timelimit_rules.TimeLimitRulesHandlers
import io.timelimit.android.ui.util.DateUtil
import io.timelimit.android.util.DayNameUtil
import io.timelimit.android.util.TimeTextUtil
import kotlin.properties.Delegates

class AppAndRuleAdapter: RecyclerView.Adapter<AppAndRuleAdapter.Holder>() {
    companion object {
        private const val APP_ENTRY = 1
        private const val ADD_APP_ITEM = 2
        private const val EXPAND_APPS_ITEM = 3
        private const val RULE_ENTRY = 4
        private const val EXPAND_RULES_ITEM = 5
        private const val RULES_INTRO = 6
        private const val ADD_RULE_ITEM = 7
        private const val HEADLINE = 8
    }

    var items: List<AppAndRuleItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var usedTimes: List<UsedTimeItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var date: DateInTimezone? by Delegates.observable(null as DateInTimezone?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].let { item ->
        when (item) {
            is AppAndRuleItem.AppEntry -> item.specifier.hashCode()
            is AppAndRuleItem.RuleEntry -> item.rule.id.hashCode()
            else -> item.hashCode()
        }
    }.toLong()

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is AppAndRuleItem.AppEntry -> APP_ENTRY
        AppAndRuleItem.AddAppItem -> ADD_APP_ITEM
        AppAndRuleItem.ExpandAppsItem -> EXPAND_APPS_ITEM
        is AppAndRuleItem.RuleEntry -> RULE_ENTRY
        AppAndRuleItem.ExpandRulesItem -> EXPAND_RULES_ITEM
        AppAndRuleItem.RulesIntro -> RULES_INTRO
        AppAndRuleItem.AddRuleItem -> ADD_RULE_ITEM
        is AppAndRuleItem.Headline -> HEADLINE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
            when (viewType) {
                APP_ENTRY -> FragmentCategoryAppsItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).also { it.root.tag = it }.root
                ADD_APP_ITEM -> AddItemViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).apply {
                    label = parent.context.getString(R.string.category_apps_add_dialog_btn_positive)

                    root.setOnClickListener { handlers?.onAddAppsClicked() }
                }.root
                EXPAND_APPS_ITEM -> ShowMoreListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).root.also { it.setOnClickListener { handlers?.onShowAllApps() } }
                RULE_ENTRY -> FragmentCategoryTimeLimitRuleItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).also { it.root.tag = it }.root
                EXPAND_RULES_ITEM -> ShowMoreListItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).root.also { it.setOnClickListener { handlers?.onShowAllRules() } }
                RULES_INTRO -> TimeLimitRuleIntroductionBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).root
                ADD_RULE_ITEM -> AddItemViewBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).apply {
                    label = parent.context.getString(R.string.category_time_limit_rule_dialog_new)

                    root.setOnClickListener { handlers?.onAddTimeLimitRuleClicked() }
                }.root
                HEADLINE -> GenericListHeaderBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).also { it.root.tag = it }.root
                else -> throw IllegalArgumentException()
            }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]

        when (item) {
            is AppAndRuleItem.AppEntry -> {
                val binding = holder.itemView.tag as FragmentCategoryAppsItemBinding
                val context = binding.root.context

                binding.title = item.title
                binding.subtitle = item.specifier.encode()
                binding.card.setOnClickListener { handlers?.onAppClicked(item) }
                binding.card.setOnLongClickListener { handlers?.onAppLongClicked(item) ?: false }
                binding.deleteButton.setOnClickListener { handlers?.onRemoveAppClicked(item) }
                binding.executePendingBindings()

                binding.icon.setImageDrawable(
                        DummyApps.getIcon(item.specifier.packageName, context) ?:
                        DefaultAppLogic.with(context)
                                .platformIntegration.getAppIcon(item.specifier.packageName)
                )
            }
            AppAndRuleItem.AddAppItem -> {/* nothing to do */}
            AppAndRuleItem.ExpandAppsItem -> {/* nothing to do */}
            is AppAndRuleItem.RuleEntry -> {
                val rule = item.rule
                val binding = holder.itemView.tag as FragmentCategoryTimeLimitRuleItemBinding
                val context = binding.root.context
                val usedTime = date?.let { date ->
                    RemainingTime.getUsedTime(
                        usedTimes = usedTimes,
                        rule = rule,
                        firstDayOfWeekAsEpochDay = date.firstDayOfWeekAsEpochDay,
                        dayOfWeekForDailyRule = if (rule.perDay) date.dayOfWeek else null
                    ).toInt()
                } ?: 0

                binding.maxTimeString = rule.maximumTimeInMillis.let { time ->
                    val timeString = TimeTextUtil.time(time, context)
                    val weeklyDailyString = context.getString(
                            if (rule.perDay) R.string.category_time_limit_rules_per_day
                            else R.string.category_time_limit_rules_per_week
                    )
                    val zeroTime = time == 0
                    val onlySingleDay = rule.dayMask.countOneBits() <= 1
                    val hideDailyWeekly = zeroTime || onlySingleDay

                    if (hideDailyWeekly) timeString else "$weeklyDailyString $timeString"
                }
                binding.usageAsText = if (rule.maximumTimeInMillis > 0) TimeTextUtil.used(usedTime, context) else null
                binding.usageProgressInPercent = if (rule.maximumTimeInMillis > 0)
                    (usedTime * 100 / rule.maximumTimeInMillis)
                else null
                binding.daysString = DayNameUtil.formatDayNameMask(rule.dayMask, context)
                binding.timeAreaString = if (rule.appliesToWholeDay)
                    null
                else
                    context.getString(
                            R.string.category_time_limit_rules_time_area,
                            MinuteOfDay.format(rule.startMinuteOfDay),
                            MinuteOfDay.format(rule.endMinuteOfDay)
                    )
                binding.expiresAtString = rule.expiresAt?.let {
                    context.getString(
                        R.string.category_time_limit_rules_expires_at_short,
                        DateUtil.formatAbsoluteDate(context, it)
                    )
                }
                binding.appliesToExtraTime = rule.applyToExtraTimeUsage && !rule.likeBlockedTimeArea
                binding.ignoredOnExtraTime = rule.maximumTimeInMillis == 0 && !rule.applyToExtraTimeUsage
                binding.sessionLimitString = if (rule.sessionDurationLimitEnabled)
                    context.getString(
                            R.string.category_time_limit_rules_session_limit,
                            TimeTextUtil.time(rule.sessionPauseMilliseconds, context),
                            TimeTextUtil.time(rule.sessionDurationMilliseconds, context)
                    )
                else
                    null

                binding.card.setOnClickListener { handlers?.onTimeLimitRuleClicked(rule) }

                binding.executePendingBindings()
            }
            AppAndRuleItem.ExpandRulesItem -> {/* nothing to do */}
            AppAndRuleItem.RulesIntro -> {/* nothing to do */}
            AppAndRuleItem.AddRuleItem -> {/* nothing to do */}
            is AppAndRuleItem.Headline -> {
                val binding = holder.itemView.tag as GenericListHeaderBinding

                binding.text = holder.itemView.context.getString(item.stringRessource)
            }
        }.let {  }
    }

    class Holder(view: View): RecyclerView.ViewHolder(view)
}

interface Handlers: AppAdapterHandlers, TimeLimitRulesHandlers {
    fun onShowAllApps()
    fun onShowAllRules()
}