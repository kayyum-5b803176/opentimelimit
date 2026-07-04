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
package io.timelimit.android.ui.homescreen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.ConfigureHomescreenDelayDialogBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder

class ConfigureHomescreenDelayDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "ConfigureHomescreenDelayDialogFragment"
        private const val STATUS_DID_LOAD_VALUE = "didLoadValue"
        private const val STATUS_CURRENT_VALUE = "currentValue"
    }

    private lateinit var binding: ConfigureHomescreenDelayDialogBinding
    private var didLoadValue = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            didLoadValue = savedInstanceState.getBoolean(STATUS_DID_LOAD_VALUE, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(STATUS_DID_LOAD_VALUE, didLoadValue)
        outState.putInt(STATUS_CURRENT_VALUE, binding.numberpicker.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val auth = (activity as ActivityViewModelHolder).getActivityViewModel()
        val database = DefaultAppLogic.with(context!!).database

        binding = ConfigureHomescreenDelayDialogBinding.inflate(inflater, container, false)

        auth.authenticatedUser.observe(viewLifecycleOwner, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        binding.numberpicker.apply {
            minValue = 3
            maxValue = 300
        }

        if (savedInstanceState != null) {
            binding.numberpicker.value = savedInstanceState.getInt(STATUS_CURRENT_VALUE)
        }

        if (!didLoadValue) {
            runAsync {
                val value = Threads.database.executeAndWait { auth.logic.database.config().getHomescreenDelaySync() }

                binding.numberpicker.value = value
                didLoadValue = true
            }
        }

        binding.saveButton.setOnClickListener {
            if (auth.isParentAuthenticated()) {
                Threads.database.execute {
                    database.config().setHomescreenDelaySync(binding.numberpicker.value)
                }

                dismiss()
            }
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}