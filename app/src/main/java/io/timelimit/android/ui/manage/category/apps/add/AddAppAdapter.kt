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
package io.timelimit.android.ui.manage.category.apps.add

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.databinding.FragmentAddCategoryAppsItemBinding
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.DummyApps
import kotlin.properties.Delegates

class AddAppAdapter: RecyclerView.Adapter<ViewHolder>() {
    var data: List<AddAppListItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var listener: AddAppAdapterListener? = null
    var selectedApps: Set<String> by Delegates.observable(emptySet()) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): AddAppListItem = data[position]
    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()
    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        FragmentAddCategoryAppsItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.apply {
            binding.title = item.title
            binding.subtitle = item.packageName
            binding.currentCategoryTitle = item.currentCategoryName
            binding.checked = selectedApps.contains(item.packageName)
            binding.card.setOnClickListener { listener?.onAppClicked(item) }
            binding.card.setOnLongClickListener { listener?.onAppLongClicked(item) ?: false }
            binding.showIcon = true
            binding.executePendingBindings()

            binding.icon.setImageDrawable(
                DummyApps.getIcon(item.packageName, context) ?:
                DefaultAppLogic.with(context)
                    .platformIntegration.getAppIcon(item.packageName)
            )
        }
    }
}

class ViewHolder(val binding: FragmentAddCategoryAppsItemBinding): RecyclerView.ViewHolder(binding.root)

interface AddAppAdapterListener {
    fun onAppClicked(app: AddAppListItem)
    fun onAppLongClicked(app: AddAppListItem): Boolean
}