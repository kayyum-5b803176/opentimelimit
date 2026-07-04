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
package io.timelimit.android.ui.manage.category.apps.addactivity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.databinding.FragmentAddCategoryAppsItemBinding
import io.timelimit.android.extensions.toggle
import kotlin.properties.Delegates

class AddAppActivityAdapter: RecyclerView.Adapter<ViewHolder>() {
    var data: List<AddActivityListItem>? by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }

    val selectedActivities = mutableSetOf<String>()

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): AddActivityListItem {
        return data!![position]
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).className.hashCode().toLong()
    }

    override fun getItemCount(): Int = this.data?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        FragmentAddCategoryAppsItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.apply {
            binding.title = item.title
            binding.currentCategoryTitle = item.currentCategoryTitle
            binding.subtitle = item.className
            binding.showIcon = false
            binding.checked = selectedActivities.contains(item.className)
            binding.executePendingBindings()

            binding.card.setOnClickListener {
                selectedActivities.toggle(item.className)
                binding.checked = selectedActivities.contains(item.className)
            }
        }
    }
}

class ViewHolder(val binding: FragmentAddCategoryAppsItemBinding): RecyclerView.ViewHolder(binding.root)