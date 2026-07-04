/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.AllowedContact
import io.timelimit.android.databinding.ContactsFragmentBinding
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.util.PhoneNumberUtils
import kotlinx.coroutines.delay


class ContactsFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        private const val LOG_TAG = "ContactsFragment"
        private const val REQ_SELECT_CONTACT = 1
        private const val REQ_CALL_PERMISSION = 2
    }

    private val model: ContactsModel by viewModels()

    private val activityModelHolder: ActivityViewModelHolder by lazy { activity as ActivityViewModelHolder }
    private val auth: ActivityViewModel by lazy { activityModelHolder.getActivityViewModel() }
    private var numberToCallWithPermission: String? = null

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue(getString(R.string.contacts_title_long))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ContactsFragmentBinding.inflate(inflater, container, false)
        val adapter = ContactsAdapter()

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                fragment = this,
                shouldHighlight = activityModelHolder.getActivityViewModel().shouldHighlightAuthenticationButton,
                authenticatedUser = activityModelHolder.getActivityViewModel().authenticatedUser,
                doesSupportAuth = liveDataFromNonNullValue(true)
        )

        binding.fab.setOnClickListener { activityModelHolder.showAuthenticationScreen() }

        model.listItems.observe(viewLifecycleOwner) { adapter.items = it }

        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter

        adapter.handlers = object: ContactsHandlers {
            override fun onAddContactClicked() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    activityModelHolder.ignoreStop = true

                    showContactSelection()
                }
            }

            override fun onContactLongClicked(item: ContactContactsItem): Boolean {
                removeItem(item.item)

                return true
            }

            override fun onContactClicked(item: ContactContactsItem) {
                startCall(item.item.phone)
            }
        }

        ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val item = adapter.items!![viewHolder.adapterPosition]

                if (item is ContactContactsItem && auth.isParentAuthenticated()) {
                    return makeMovementFlags(0, ItemTouchHelper.START or ItemTouchHelper.END)
                } else if (item is IntroContactsItem) {
                    return makeMovementFlags(0, ItemTouchHelper.START or ItemTouchHelper.END)
                }

                return 0
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                // ignore

                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.items!![viewHolder.adapterPosition]

                if (item is ContactContactsItem) {
                    removeItem(item.item)
                } else if (item is IntroContactsItem) {
                    model.hideIntro()
                }
            }
        }).attachToRecyclerView(binding.recycler)

        return binding.root
    }

    private fun showContactSelection() {
        try {
            startActivityForResult(
                    Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                            .setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE),
                    REQ_SELECT_CONTACT
            )
        } catch (ex: Exception) {
            Snackbar.make(requireView(), R.string.error_general, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun removeItem(item: AllowedContact) {
        if (auth.isParentAuthenticated()) {
            model.removeContact(item.id)

            Snackbar.make(requireView(), getString(R.string.contacts_snackbar_removed, item.title), Snackbar.LENGTH_SHORT)
                    .setAction(R.string.generic_undo) {
                        model.addContact(item)
                    }
                    .show()
        } else {
            Snackbar.make(requireView(), R.string.contacts_snackbar_remove_auth, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun startCall(number: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + PhoneNumberUtils.normalizeNumber(number)))
                val resolveInfo = requireContext().packageManager.queryIntentActivities(intent, 0)

                if (resolveInfo.size > 1) {
                    SelectDialerDialogFragment.newInstance(intent, this).show(parentFragmentManager)
                } else {
                    startCall(intent)
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not start call", ex)
                }

                Snackbar.make(requireView(), R.string.contacts_snackbar_call_failed, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            numberToCallWithPermission = number
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PERMISSION)
        }
    }

    fun startCall(intent: Intent) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val logic = DefaultAppLogic.with(requireContext())

            try {
                logic.backgroundTaskLogic.pauseForegroundAppBackgroundLoop = true

                startActivity(intent)

                runAsync {
                    delay(500)

                    startActivity(
                            Intent(requireContext(), MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    )

                    delay(500)

                    logic.backgroundTaskLogic.pauseForegroundAppBackgroundLoop = false

                    Snackbar.make(requireView(), R.string.contacts_snackbar_call_started, Snackbar.LENGTH_LONG).show()
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "could not start call", ex)
                }

                logic.backgroundTaskLogic.pauseForegroundAppBackgroundLoop = false

                Snackbar.make(requireView(), R.string.contacts_snackbar_call_failed, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            Snackbar.make(requireView(), R.string.contacts_snackbar_call_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_SELECT_CONTACT) {
            activityModelHolder.ignoreStop = false

            if (resultCode == Activity.RESULT_OK) {
                try {
                    data?.data?.let { contactData ->
                        val cursor = requireContext().contentResolver.query(
                            contactData,
                            null,
                            null,
                            null,
                            null
                        )

                        cursor?.use {
                            if (cursor.moveToFirst()) {
                                val title =
                                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                                val phoneNumber =
                                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))

                                model.addContact(title = title, phoneNumber = phoneNumber)

                                Snackbar.make(
                                    requireView(),
                                    R.string.contacts_snackbar_added,
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "could not add contact", ex)
                    }

                    Toast.makeText(requireContext(), R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_CALL_PERMISSION) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                numberToCallWithPermission?.let { number -> startCall(number) }
            }
        }
    }
}
