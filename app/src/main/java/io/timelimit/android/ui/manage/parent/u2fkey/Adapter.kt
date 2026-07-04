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
package io.timelimit.android.ui.manage.parent.u2fkey

import android.text.format.DateUtils
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import io.timelimit.android.R
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.ManageParentU2fKeyItemBinding
import kotlin.properties.Delegates

class Adapter() : RecyclerView.Adapter<Adapter.Holder>() {
    companion object {
        private const val TYPE_ADD = 1
        private const val TYPE_KEY = 2
    }

    var items: List<U2FKeyListItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var listener: Handlers? = null

    init { setHasStableIds(true) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = when (viewType) {
        TYPE_ADD -> Holder.Add(AddItemViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)).also {
            it.binding.label = parent.context.getString(R.string.manage_parent_u2f_add_key)
            it.binding.root.setOnClickListener { listener?.onAddKeyClicked() }
        }
        TYPE_KEY -> Holder.Key(ManageParentU2fKeyItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else -> throw IllegalArgumentException()
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]

        if (holder is Holder.Key && item is U2FKeyListItem.KeyItem) {
            holder.binding.also { binding ->
                binding.id = item.item.publicKey
                    .sliceArray(0 until item.item.publicKey.size.coerceAtMost(4))
                    .let { Base64.encodeToString(it, Base64.NO_WRAP or Base64.NO_PADDING) }

                binding.addedAt = DateUtils.getRelativeTimeSpanString(
                    item.item.addedAt,
                    System.currentTimeMillis(),
                    DateUtils.HOUR_IN_MILLIS
                ).toString()

                binding.removeButton.setOnClickListener { listener?.onRemoveClicked(item) }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is U2FKeyListItem.AddKey -> TYPE_ADD
        is U2FKeyListItem.KeyItem -> TYPE_KEY
    }

    override fun getItemId(position: Int): Long = items[position].let { item ->
        when (item) {
            U2FKeyListItem.AddKey -> item.hashCode().toLong()
            is U2FKeyListItem.KeyItem -> item.item.keyId
        }
    }

    sealed class Holder(view: View): RecyclerView.ViewHolder(view) {
        class Add(val binding: AddItemViewBinding): Holder(binding.root)
        class Key(val binding: ManageParentU2fKeyItemBinding): Holder(binding.root)
    }

    interface Handlers {
        fun onAddKeyClicked()
        fun onRemoveClicked(keyItem: U2FKeyListItem.KeyItem)
    }
}