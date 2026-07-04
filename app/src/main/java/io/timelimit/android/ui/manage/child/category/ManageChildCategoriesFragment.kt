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
package io.timelimit.android.ui.manage.child.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.databinding.RecyclerFragmentBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryDisableLimitsAction
import io.timelimit.android.sync.actions.UpdateCategorySortingAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.child.ManageChildFragmentArgs
import io.timelimit.android.ui.manage.child.ManageChildFragmentDirections
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import io.timelimit.android.ui.manage.child.category.specialmode.SetCategorySpecialModeFragment
import io.timelimit.android.ui.manage.child.category.specialmode.SpecialModeDialogMode

class ManageChildCategoriesFragment : Fragment() {
    companion object {
        fun newInstance(params: ManageChildFragmentArgs) = ManageChildCategoriesFragment().apply {
            arguments = params.toBundle()
        }
    }

    private val params: ManageChildFragmentArgs by lazy { ManageChildFragmentArgs.fromBundle(requireArguments()) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val model: ManageChildCategoriesModel by viewModels()
    private lateinit var binding: RecyclerFragmentBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = RecyclerFragmentBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = Adapter()
        val navigation = Navigation.findNavController(view)

        adapter.handlers = object: Handlers {
            override fun onCategoryClicked(category: Category) {
                navigation.safeNavigate(
                        ManageChildFragmentDirections.actionManageChildFragmentToManageCategoryFragment(
                                params.childId,
                                category.id
                        ),
                        R.id.manageChildFragment
                )
            }

            override fun onCreateCategoryClicked() {
                if (auth.requestAuthenticationOrReturnTrueAllowChild(childId = params.childId)) {
                    CreateCategoryDialogFragment.newInstance(childId = params.childId)
                            .show(parentFragmentManager)
                }
            }

            override fun onCategorySwitched(category: CategoryItem, isChecked: Boolean): Boolean {
                return if (isChecked) {
                    if (auth.isParentAuthenticated()) {
                        auth.tryDispatchParentActions(
                                listOf(
                                        UpdateCategoryTemporarilyBlockedAction(
                                                categoryId = category.category.id,
                                                endTime = null,
                                                blocked = false
                                        ),
                                        UpdateCategoryDisableLimitsAction(
                                                categoryId = category.category.id,
                                                endTime = 0
                                        )
                                )
                        )
                    } else if (auth.isParentOrChildAuthenticated(params.childId) && category.mode is CategorySpecialMode.TemporarilyAllowed) {
                        ConfirmEnableLimitsAgainDialogFragment.newInstance(
                            childId = params.childId,
                            categoryId = category.category.id
                        ).show(parentFragmentManager); false
                    } else if (
                            auth.isParentOrChildAuthenticated(params.childId) &&
                            (!(category.mode is CategorySpecialMode.TemporarilyBlocked && category.mode.endTime == null))
                    ) {
                        SetCategorySpecialModeFragment.newInstance(
                                childId = params.childId,
                                categoryId = category.category.id,
                                mode = if (auth.isParentAuthenticated()) SpecialModeDialogMode.Regular else SpecialModeDialogMode.SelfLimitAdd
                        ).show(parentFragmentManager)

                        false
                    } else { auth.requestAuthentication(); false }
                } else /* if (!isChecked) */ {
                    if (auth.requestAuthenticationOrReturnTrueAllowChild(childId = params.childId)) {
                        SetCategorySpecialModeFragment.newInstance(
                                childId = params.childId,
                                categoryId = category.category.id,
                                mode = if (auth.isParentAuthenticated()) SpecialModeDialogMode.Regular else SpecialModeDialogMode.SelfLimitAdd
                        ).show(parentFragmentManager)
                    }

                    false
                }
            }
        }

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(context)

        model.init(params.childId)
        model.listContent.observe(viewLifecycleOwner, Observer { adapter.categories = it })

        ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val index = viewHolder.adapterPosition
                val item = if (index == RecyclerView.NO_POSITION) null else adapter.categories!![index]

                if (item == CategoriesIntroductionHeader) {
                    return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.END or ItemTouchHelper.START) or
                            makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END or ItemTouchHelper.START)
                } else if (item is CategoryItem) {
                    return makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, ItemTouchHelper.UP or ItemTouchHelper.DOWN) or
                            makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.UP or ItemTouchHelper.DOWN)
                } else {
                    return 0
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromIndex = viewHolder.adapterPosition
                val toIndex = target.adapterPosition
                val categories = adapter.categories!!

                if (fromIndex == RecyclerView.NO_POSITION || toIndex == RecyclerView.NO_POSITION) {
                    return false
                }

                val fromItem = categories[fromIndex]
                val toItem = categories[toIndex]

                if (!(fromItem is CategoryItem)) {
                    throw IllegalStateException()
                }

                val relatedCategories = categories
                        .filter { it is CategoryItem && it.categoryNestingLevel == fromItem.categoryNestingLevel && it.parentCategoryId == fromItem.parentCategoryId }

                val sourceIndex = relatedCategories.indexOf(fromItem)
                val targetIndex = relatedCategories.indexOf(toItem)

                if (targetIndex == -1) {
                    return false
                }

                val updatedRelatedCategories = relatedCategories.map { it as CategoryItem; it.category.id }.toMutableList()
                updatedRelatedCategories.add(targetIndex, updatedRelatedCategories.removeAt(sourceIndex))

                return auth.tryDispatchParentAction(
                        UpdateCategorySortingAction(
                                categoryIds = updatedRelatedCategories
                        )
                )
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val database = logic.database

                Threads.database.submit {
                    database.config().setHintsShownSync(HintsToShow.CATEGORIES_INTRODUCTION)
                }
            }
        }).attachToRecyclerView(binding.recycler)
    }
}
