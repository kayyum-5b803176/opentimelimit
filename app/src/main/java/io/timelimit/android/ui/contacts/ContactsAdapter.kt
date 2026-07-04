/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.ContactsItemBinding
import kotlin.properties.Delegates

class ContactsAdapter: RecyclerView.Adapter<ContactsViewHolder>() {
    companion object {
        private const val TYPE_INTRO = 1
        private const val TYPE_ITEM = 2
        private const val TYPE_ADD = 3
    }

    var items: List<ContactsItem>? by Delegates.observable(null as List<ContactsItem>?) { _, _, _ -> notifyDataSetChanged() }
    var handlers: ContactsHandlers? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = items?.size ?: 0

    override fun getItemId(position: Int): Long {
        val item = items!![position]

        return when (item) {
            is IntroContactsItem -> Long.MAX_VALUE
            is AddContactsItem -> Long.MAX_VALUE - 1
            is ContactContactsItem -> item.item.id.toLong()
        }
    }

    override fun getItemViewType(position: Int): Int = when (items!![position]) {
        is IntroContactsItem -> TYPE_INTRO
        is ContactContactsItem -> TYPE_ITEM
        is AddContactsItem -> TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactsViewHolder = when (viewType) {
        TYPE_INTRO -> ContactsStaticHolder(
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.contacts_intro, parent, false)
        )
        TYPE_ITEM -> ContactsItemHolder(
                ContactsItemBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                )
        )
        TYPE_ADD -> ContactsStaticHolder(
                AddItemViewBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                ).let {
                    it.label = parent.context.getString(R.string.contacts_add)

                    it.root.setOnClickListener {
                        handlers?.onAddContactClicked()
                    }

                    it.root
                }
        )
        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ContactsViewHolder, position: Int) {
        when (holder) {
            is ContactsStaticHolder ->  {/* nothing to do */}
            is ContactsItemHolder -> {
                val item = items!![position]

                item as ContactContactsItem

                holder.view.title = item.item.title
                holder.view.phone = item.item.phone

                holder.view.card.setOnClickListener { handlers?.onContactClicked(item) }
                holder.view.card.setOnLongClickListener { handlers?.onContactLongClicked(item) ?: false }

                holder.view.executePendingBindings()

                null
            }
        }.let {/* require handling all cases */}
    }
}

sealed class ContactsViewHolder(root: View): RecyclerView.ViewHolder(root)
class ContactsStaticHolder(root: View): ContactsViewHolder(root)
class ContactsItemHolder(val view: ContactsItemBinding): ContactsViewHolder(view.root)

interface ContactsHandlers {
    fun onAddContactClicked()
    fun onContactLongClicked(item: ContactContactsItem): Boolean
    fun onContactClicked(item: ContactContactsItem)
}