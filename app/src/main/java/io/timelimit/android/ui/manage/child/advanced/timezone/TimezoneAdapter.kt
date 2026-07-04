/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.ui.manage.child.advanced.timezone

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.extensions.readableName
import java.util.*
import kotlin.properties.Delegates

class TimezoneAdapter: RecyclerView.Adapter<TimezoneViewHolder>() {
    var timezones: List<TimeZone> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var listener: TimezoneAdapterListener? = null

    override fun getItemCount(): Int = timezones.size
    override fun getItemId(position: Int): Long = timezones[position].id.hashCode().toLong()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TimezoneViewHolder(
            LayoutInflater.from(parent.context)
                    .inflate(R.layout.simple_list_item, parent, false) as TextView
    )

    override fun onBindViewHolder(holder: TimezoneViewHolder, position: Int) {
        val item = timezones[position]

        holder.view.text = item.readableName()
        holder.view.setOnClickListener {
            listener?.onTimezoneClicked(item)
        }
    }
}

class TimezoneViewHolder(val view: TextView): RecyclerView.ViewHolder(view)

interface TimezoneAdapterListener {
    fun onTimezoneClicked(timeZone: TimeZone)
}