/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.SetChildTimezoneDialogFragmentBinding
import io.timelimit.android.extensions.readableName
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SetUserTimezoneAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import java.util.*

class SetUserTimezoneDialogFragment : DialogFragment() {
    companion object {
        private const val EXTRA_USER_ID = "userId"
        private const val DIALOG_TAG = "SetUserTimezoneDialogFragment"

        fun newInstance(userId: String) = SetUserTimezoneDialogFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_USER_ID, userId)
            }
        }
    }

    val userId: String by lazy {
        arguments!!.getString(EXTRA_USER_ID)!!
    }

    val auth: ActivityViewModel by lazy {
        getActivityViewModel(activity!!)
    }

    val logic: AppLogic by lazy {
        DefaultAppLogic.with(context!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = SetChildTimezoneDialogFragmentBinding.inflate(LayoutInflater.from(context!!))
        val adapter = TimezoneAdapter()

        adapter.listener = object: TimezoneAdapterListener {
            override fun onTimezoneClicked(timeZone: TimeZone) {
                auth.tryDispatchParentAction(
                        SetUserTimezoneAction(
                                userId = userId,
                                timezone = timeZone.id
                        )
                )

                dismiss()
            }
        }

        binding.recycler.layoutManager = LinearLayoutManager(context!!)
        binding.recycler.adapter = adapter

        val searchTerm = MutableLiveData<String>().apply { value = binding.searchField.text.toString() }
        binding.searchField.addTextChangedListener(object: TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchTerm.value = binding.searchField.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
                // ignore
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // ignore
            }
        })

        val zones = listOf(logic.timeApi.getSystemTimeZone()) + (TimeZone.getAvailableIDs().map { zoneId -> TimeZone.getTimeZone(zoneId) })

        searchTerm.map { term ->
            zones.filter { it.readableName().contains(term, ignoreCase = true) }
        }.observe(this, Observer {
            adapter.timezones = it
        })

        return AlertDialog.Builder(context!!, R.style.FullscreenDialogTheme)
                .setView(binding.root)
                .setNegativeButton(R.string.generic_cancel, null)
                .create()
                .apply {
                    setOnShowListener {
                        dialog!!.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}
