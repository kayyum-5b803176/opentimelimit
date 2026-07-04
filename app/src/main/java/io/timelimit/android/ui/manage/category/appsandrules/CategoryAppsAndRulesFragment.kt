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
import io.timelimit.android.async.Threads
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.databinding.FragmentCategoryAppsAndRulesBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.CreateTimeLimitRuleAction
import io.timelimit.android.sync.actions.RemoveCategoryAppsAction
import io.timelimit.android.sync.actions.UpdateTimeLimitRuleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.apps.add.AddAppsParams
import io.timelimit.android.ui.manage.category.apps.add.AddCategoryAppsFragment
import io.timelimit.android.ui.manage.category.timelimit_rules.edit.EditTimeLimitRuleDialogFragment
import io.timelimit.android.ui.manage.category.timelimit_rules.edit.EditTimeLimitRuleDialogFragmentListener
import io.timelimit.android.ui.manage.child.apps.assign.AssignAppCategoryDialogFragment

abstract class CategoryAppsAndRulesFragment: Fragment(), Handlers, EditTimeLimitRuleDialogFragmentListener {
    private val adapter = AppAndRuleAdapter().also { it.handlers = this }
    val model: AppsAndRulesModel by viewModels()
    val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    val database: Database by lazy { DefaultAppLogic.with(requireContext()).database }
    abstract val childId: String
    abstract val categoryId: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentCategoryAppsAndRulesBinding.inflate(inflater, container, false)

        model.init(userId = childId, categoryId = categoryId)

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        model.dateAndUsedTimes.observe(viewLifecycleOwner) { (date, usedTimes) ->
            adapter.date = date
            adapter.usedTimes = usedTimes
        }

        ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val index = viewHolder.adapterPosition
                val item = if (index == RecyclerView.NO_POSITION) null else adapter.items[index]

                if (item == AppAndRuleItem.RulesIntro) {
                    return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.END or ItemTouchHelper.START) or
                            makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END or ItemTouchHelper.START)
                } else {
                    return 0
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = throw IllegalStateException()

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val database = database

                Threads.database.submit {
                    database.config().setHintsShownSync(HintsToShow.TIME_LIMIT_RULE_INTRODUCTION)
                }
            }
        }).attachToRecyclerView(binding.recycler)

        return binding.root
    }

    fun setListContent(items: List<AppAndRuleItem>) { adapter.items = items }

    override fun notifyRuleCreated() {
        Snackbar.make(requireView(), R.string.category_time_limit_rules_snackbar_created, Snackbar.LENGTH_SHORT)
                .show()
    }

    override fun notifyRuleDeleted(oldRule: TimeLimitRule) {
        Snackbar.make(requireView(), R.string.category_time_limit_rules_snackbar_deleted, Snackbar.LENGTH_SHORT)
                .setAction(R.string.generic_undo) {
                    auth.tryDispatchParentAction(
                            CreateTimeLimitRuleAction(
                                    rule = oldRule
                            )
                    )
                }
                .show()
    }

    override fun notifyRuleUpdated(oldRule: TimeLimitRule, newRule: TimeLimitRule) {
        Snackbar.make(requireView(), R.string.category_time_limit_rules_snackbar_updated, Snackbar.LENGTH_SHORT)
            .also {
                if (auth.isParentAuthenticated()) it.setAction(R.string.generic_undo) {
                    auth.tryDispatchParentAction(
                        UpdateTimeLimitRuleAction(
                            ruleId = oldRule.id,
                            applyToExtraTimeUsage = oldRule.applyToExtraTimeUsage,
                            maximumTimeInMillis = oldRule.maximumTimeInMillis,
                            dayMask = oldRule.dayMask,
                            start = oldRule.startMinuteOfDay,
                            end = oldRule.endMinuteOfDay,
                            sessionDurationMilliseconds = oldRule.sessionDurationMilliseconds,
                            sessionPauseMilliseconds = oldRule.sessionPauseMilliseconds,
                            perDay = oldRule.perDay,
                            expiresAt = oldRule.expiresAt
                        )
                    )
                }
            }.show()
    }

    override fun onAppClicked(app: AppAndRuleItem.AppEntry) {
        AppClickedDialogFragment.newInstance().show(childFragmentManager)
    }

    override fun onRemoveAppClicked(app: AppAndRuleItem.AppEntry) {
        if (auth.tryDispatchParentAction(
                        RemoveCategoryAppsAction(
                                categoryId = categoryId,
                                packageNames = listOf(app.specifier.encode())
                        )
                )) {
            Snackbar.make(requireView(), getString(R.string.category_apps_item_removed_toast, app.title), Snackbar.LENGTH_SHORT)
                    .setAction(R.string.generic_undo) {
                        auth.tryDispatchParentAction(
                                AddCategoryAppsAction(
                                        categoryId = categoryId,
                                        packageNames = listOf(app.specifier.encode())
                                )
                        )
                    }
                    .show()
        }
    }

    override fun onAppLongClicked(app: AppAndRuleItem.AppEntry): Boolean {
        if (auth.requestAuthenticationOrReturnTrue()) {
            AssignAppCategoryDialogFragment.newInstance(
                childId = childId,
                appPackageName = app.specifier.encode()
            ).show(parentFragmentManager)
        }

        // consume the click in any case
        return true
    }

    override fun onAddAppsClicked() {
        if (auth.requestAuthenticationOrReturnTrueAllowChild(childId = childId)) {
            AddCategoryAppsFragment.newInstance(AddAppsParams(
                    childId = childId,
                    categoryId = categoryId,
                    isSelfLimitAddingMode = !auth.isParentAuthenticated()
            )).show(parentFragmentManager)
        }
    }

    override fun onTimeLimitRuleClicked(rule: TimeLimitRule) {
        if (auth.isParentAuthenticated()) {
            EditTimeLimitRuleDialogFragment.newInstance(rule, false,  this).show(parentFragmentManager)
        } else if (auth.isParentOrChildAuthenticated(childId)) {
            EditTimeLimitRuleDialogFragment.newInstance(rule, true, this).show(parentFragmentManager)
        } else auth.requestAuthentication()
    }

    override fun onAddTimeLimitRuleClicked() {
        if (auth.requestAuthenticationOrReturnTrueAllowChild(childId = childId)) {
            EditTimeLimitRuleDialogFragment.newInstance(categoryId, true, this).show(parentFragmentManager)
        }
    }

    override fun onShowAllApps() { model.showAllApps() }
    override fun onShowAllRules() { model.showAllRules() }
}