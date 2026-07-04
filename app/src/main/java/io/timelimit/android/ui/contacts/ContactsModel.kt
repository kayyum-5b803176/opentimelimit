/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.AllowedContact
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.logic.DefaultAppLogic

class ContactsModel(application: Application): AndroidViewModel(application) {
    private val appLogic = DefaultAppLogic.with(application)
    private val allowedContacts = appLogic.database.allowedContact().getAllowedContactsLive()
    private val didHideIntro = appLogic.database.config().wereHintsShown(HintsToShow.CONTACTS_INTRO)

    private val convertedContactItems = allowedContacts.map { items -> items.map { ContactContactsItem(it) } }
    private val baseListItems = convertedContactItems.map { list -> list + listOf(AddContactsItem) }

    val listItems = didHideIntro.switchMap { hideIntro ->
        baseListItems.map { baseItems ->
            if (hideIntro) {
                baseItems
            } else {
                listOf(IntroContactsItem) + baseItems
            }
        }
    }

    fun addContact(title: String, phoneNumber: String) {
        Threads.database.submit {
            appLogic.database.allowedContact().addContactSync(AllowedContact(
                    id = 0,
                    phone = phoneNumber,
                    title = title
            ))
        }
    }

    fun addContact(item: AllowedContact) {
        Threads.database.submit { appLogic.database.allowedContact().addContactSync(item) }
    }

    fun removeContact(id: Int) {
        Threads.database.submit { appLogic.database.allowedContact().removeContactSync(id) }
    }

    fun hideIntro() {
        Threads.database.submit { appLogic.database.config().setHintsShownSync(HintsToShow.CONTACTS_INTRO) }
    }
}