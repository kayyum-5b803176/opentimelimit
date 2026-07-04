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
package io.timelimit.android.ui.manage.child.apps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.App
import io.timelimit.android.databinding.FragmentChildAppsItemBinding
import io.timelimit.android.databinding.GenericBigListHeaderBinding
import io.timelimit.android.databinding.GenericListHeaderBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.DummyApps
import kotlin.properties.Delegates

class ChildAppsAdapter: RecyclerView.Adapter<ChildAppsHolder>() {
    companion object {
        private const val TYPE_CATEGORY_HEADER = 0
        private const val TYPE_APP = 1
        private const val TYPE_EMPTY = 2
    }

    var data: List<ChildAppsEntry> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = data.size
    override fun getItemViewType(position: Int): Int = when (data[position]) {
        is ChildAppsCategoryHeader -> TYPE_CATEGORY_HEADER
        is ChildAppsApp -> TYPE_APP
        is ChildAppsEmptyCategory -> TYPE_EMPTY
    }

    override fun getItemId(position: Int): Long {
        val item = data[position]

        return when (item) {
            is ChildAppsCategoryHeader -> (item.categoryId ?: "no category").hashCode().toLong()
            is ChildAppsApp -> item.app.packageName.hashCode().toLong()
            is ChildAppsEmptyCategory -> (item.categoryId ?: "no category").hashCode().toLong()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildAppsHolder = when (viewType) {
        TYPE_CATEGORY_HEADER -> CategoryHeaderHolder(
                GenericBigListHeaderBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
        )
        TYPE_APP -> AppHolder(
                FragmentChildAppsItemBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
        )
        TYPE_EMPTY -> CategoryEmptyHolder(
                GenericListHeaderBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                ).apply {
                    text = parent.context.getString(R.string.child_apps_empty_category)
                    executePendingBindings()
                }.root
        )
        else -> throw IllegalArgumentException()
    }

    override fun onBindViewHolder(holder: ChildAppsHolder, position: Int) {
        val item = data[position]

        when (item) {
            is ChildAppsCategoryHeader -> {
                holder as CategoryHeaderHolder

                holder.binding.text = item.title
                holder.binding.executePendingBindings()

                null
            }
            is ChildAppsApp -> {
                holder as AppHolder

                val context = holder.binding.root.context

                holder.binding.item = item.app
                holder.binding.currentCategoryTitle = item.shownCategoryName
                holder.binding.icon.setImageDrawable(
                        DummyApps.getIcon(item.app.packageName, context) ?:
                        DefaultAppLogic.with(context).platformIntegration.getAppIcon(item.app.packageName)
                )
                holder.binding.handlers = handlers
                holder.binding.executePendingBindings()

                null
            }
            is ChildAppsEmptyCategory -> {
                holder as CategoryEmptyHolder

                // nothing to do
            }
        }.let { /* require handling all paths */ }
    }
}

sealed class ChildAppsHolder(view: View): RecyclerView.ViewHolder(view)
class CategoryHeaderHolder(val binding: GenericBigListHeaderBinding): ChildAppsHolder(binding.root)
class AppHolder(val binding: FragmentChildAppsItemBinding): ChildAppsHolder(binding.root)
class CategoryEmptyHolder(view: View): ChildAppsHolder(view)
class AssignAllAppsViewHolder(view: View): ChildAppsHolder(view)

interface Handlers {
    fun onAppClicked(app: App)
}
