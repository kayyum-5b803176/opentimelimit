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
package io.timelimit.android.ui.manage.child.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.CategoryRichCardBinding
import io.timelimit.android.databinding.IntroCardBinding
import io.timelimit.android.ui.util.DateUtil
import io.timelimit.android.util.TimeTextUtil
import kotlin.properties.Delegates

class Adapter: RecyclerView.Adapter<ViewHolder>() {
    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD = 1
        private const val TYPE_INTRO = 2
        private const val TYPE_MANIPULATION_WARNING = 3
    }

    var categories: List<ManageChildCategoriesListItem>? by Delegates.observable(null as List<ManageChildCategoriesListItem>?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? by Delegates.observable(null as Handlers?) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int) = categories!![position]
    override fun getItemId(position: Int): Long {
        val item = getItem(position)

        return when (item) {
            is CategoryItem -> item.category.id.hashCode()
            CreateCategoryItem -> item.hashCode()
            CategoriesIntroductionHeader -> item.hashCode()
            ManipulationWarningCategoryItem -> item.hashCode()
        }.toLong()
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is CategoryItem -> TYPE_ITEM
        CreateCategoryItem -> TYPE_ADD
        CategoriesIntroductionHeader -> TYPE_INTRO
        ManipulationWarningCategoryItem -> TYPE_MANIPULATION_WARNING
    }

    override fun getItemCount() = categories?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        TYPE_ADD ->
            AddViewHolder(
                    AddItemViewBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    ).apply {
                        label = parent.context.getString(R.string.create_category_title)

                        root.setOnClickListener {
                            handlers?.onCreateCategoryClicked()
                        }
                    }.root
            )

        TYPE_ITEM ->
            ItemViewHolder(
                    CategoryRichCardBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )

        TYPE_INTRO ->
            IntroViewHolder(
                    IntroCardBinding.inflate(LayoutInflater.from(parent.context), parent, false).also {
                        it.title = parent.context.getString(R.string.manage_child_categories_intro_title)
                        it.text = parent.context.getString(R.string.manage_child_categories_intro_text)
                    }.root
            )

        TYPE_MANIPULATION_WARNING ->
            ManipulationWarningViewHolder(
                    LayoutInflater.from(parent.context)
                            .inflate(R.layout.manage_child_manipulation_warning, parent, false)
            )

        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        when (item) {
            is CategoryItem -> {
                holder as ItemViewHolder

                val binding = holder.binding
                val context = binding.root.context

                binding.title = item.category.title
                binding.remainingTimeToday = if (item.remainingTimeToday != null) {
                    TimeTextUtil.remaining(item.remainingTimeToday.toInt(), context)
                } else {
                    null
                }
                binding.usedTimeToday = if (item.usedTimeToday != 0L) {
                    TimeTextUtil.used(item.usedTimeToday.toInt(), context)
                } else {
                    null
                }
                binding.usedForAppsWithoutCategory = item.usedForNotAssignedApps
                binding.leftSpace.layoutParams = LinearLayout.LayoutParams(
                        CategoryItemLeftPadding.calculate(item.categoryNestingLevel, context),
                        0
                )
                binding.isTemporarilyBlocked = item.mode is CategorySpecialMode.TemporarilyBlocked
                binding.temporarilyBlockedUntil = if (item.mode is CategorySpecialMode.TemporarilyBlocked)
                    item.mode.endTime?.let {  DateUtil.formatAbsoluteDate(context, it) } else null

                binding.limitsDisabledUntil = if (item.mode is CategorySpecialMode.TemporarilyAllowed)
                    DateUtil.formatAbsoluteDate(context, item.mode.endTime) else null

                binding.card.setOnClickListener { handlers?.onCategoryClicked(item.category) }

                val shouldBeChecked = item.mode == CategorySpecialMode.None
                binding.categorySwitch.setOnCheckedChangeListener { _, _ ->  }
                binding.categorySwitch.isChecked = shouldBeChecked
                binding.categorySwitch.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != shouldBeChecked) {
                        if (handlers?.onCategorySwitched(item, isChecked) != true)
                            binding.categorySwitch.isChecked = shouldBeChecked
                    }
                }

                binding.executePendingBindings()
            }
            CreateCategoryItem -> {
                // nothing to do
            }
            CategoriesIntroductionHeader -> {
                // nothing to do
            }
            ManipulationWarningCategoryItem -> {
                // nothing to do
            }
        }.let {  }
    }
}

sealed class ViewHolder(view: View): RecyclerView.ViewHolder(view)
class AddViewHolder(view: View): ViewHolder(view)
class IntroViewHolder(view: View): ViewHolder(view)
class ManipulationWarningViewHolder(view: View): ViewHolder(view)
class ItemViewHolder(val binding: CategoryRichCardBinding): ViewHolder(binding.root)

interface Handlers {
    fun onCategoryClicked(category: Category)
    fun onCreateCategoryClicked()
    fun onCategorySwitched(category: CategoryItem, isChecked: Boolean): Boolean
}
