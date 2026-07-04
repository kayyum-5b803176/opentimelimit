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
package io.timelimit.android.ui.diagnose.exitreason

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.databinding.DiagnoseExitReasonItemBinding
import io.timelimit.android.integration.platform.ExitLogItem
import java.text.DateFormat
import java.util.*
import kotlin.properties.Delegates

class DiagnoseExitReasonAdapter: RecyclerView.Adapter<DiagnoseExitReasonHolder>() {
    var content: List<ExitLogItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiagnoseExitReasonHolder = DiagnoseExitReasonHolder(
        DiagnoseExitReasonItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: DiagnoseExitReasonHolder, position: Int) {
        val item = content[position]
        val view = holder.binding

        view.timeString = DateFormat.getDateTimeInstance().format(Date(item.timestamp))
        view.summaryString = item.reason.toString()
        view.detailString = item.description
    }

    override fun getItemCount(): Int = content.size
}

class DiagnoseExitReasonHolder(val binding: DiagnoseExitReasonItemBinding): RecyclerView.ViewHolder(binding.root)