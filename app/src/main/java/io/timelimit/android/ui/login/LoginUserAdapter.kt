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
package io.timelimit.android.ui.login

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.ui.list.TextViewHolder
import kotlin.properties.Delegates

class LoginUserAdapter : RecyclerView.Adapter<TextViewHolder>() {
    var data: List<LoginUserAdapterItem>? by Delegates.observable(null as List<LoginUserAdapterItem>?) { _, _, _ -> notifyDataSetChanged() }
    var listener: LoginUserAdapterListener? by Delegates.observable(null as LoginUserAdapterListener?) { _, _, _ -> notifyDataSetChanged() }

    init {
        setHasStableIds(true)
    }

    fun getItem(position: Int): LoginUserAdapterItem {
        return data!![position]
    }

    override fun getItemCount(): Int {
        val data = this.data

        if (data == null) {
            return 0
        } else {
            return data.size
        }
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)

        return when (item) {
            is LoginUserAdapterUser -> item.item.id.hashCode().toLong()
            LoginUserAdapterScan -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        return TextViewHolder(parent)
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        val item = getItem(position)
        val listener = this.listener

        holder.textView.text = when (item) {
            is LoginUserAdapterUser -> item.item.name
            LoginUserAdapterScan -> holder.textView.context.getString(R.string.login_scan_code)
        }

        if (listener !=null) {
            holder.textView.setOnClickListener {
                when (item) {
                    is LoginUserAdapterUser -> listener.onUserClicked(item.item)
                    LoginUserAdapterScan -> listener.onScanCodeRequested()
                }
            }
        } else {
            holder.textView.setOnClickListener(null)
        }
    }
}

interface LoginUserAdapterListener {
    fun onUserClicked(user: User)
    fun onScanCodeRequested()
}

sealed class LoginUserAdapterItem
data class LoginUserAdapterUser(val item: User): LoginUserAdapterItem()
object LoginUserAdapterScan: LoginUserAdapterItem()