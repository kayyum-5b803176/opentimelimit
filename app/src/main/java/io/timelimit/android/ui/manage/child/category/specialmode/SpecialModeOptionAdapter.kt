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
package io.timelimit.android.ui.manage.child.category.specialmode

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.ui.list.TextViewHolder
import kotlin.properties.Delegates

class SpecialModeOptionAdapter: RecyclerView.Adapter<TextViewHolder>() {
    var items: List<SpecialModeOption> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var listener: SpecialModeOptionListener? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = items.size
    override fun getItemId(position: Int): Long = items[position].hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder = TextViewHolder(parent)

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        val item = items[position]

        holder.textView.setText(item.getLabel(holder.textView.context))
        holder.textView.setOnClickListener { listener?.onItemClicked(item) }
    }
}

interface SpecialModeOptionListener {
    fun onItemClicked(item: SpecialModeOption)
}