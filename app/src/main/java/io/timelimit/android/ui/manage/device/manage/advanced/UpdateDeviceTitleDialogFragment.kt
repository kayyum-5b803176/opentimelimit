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
package io.timelimit.android.ui.manage.device.manage.advanced

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.UserType
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.waitForNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateDeviceNameAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.util.EditTextBottomSheetDialog

class UpdateDeviceTitleDialogFragment: EditTextBottomSheetDialog() {
    companion object {
        private const val TAG = "UpdateDeviceTitleDialogFragment"
        private const val EXTRA_DEVICE_ID = "deviceId"

        fun newInstance(deviceId: String) = UpdateDeviceTitleDialogFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_DEVICE_ID, deviceId)
            }
        }
    }

    val deviceId: String by lazy { arguments!!.getString(EXTRA_DEVICE_ID)!! }
    val auth: ActivityViewModel by lazy {
        (activity as ActivityViewModelHolder).getActivityViewModel()
    }
    val deviceEntry: LiveData<Device?> by lazy {
        DefaultAppLogic.with(context!!).database.device().getDeviceById(deviceId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        deviceEntry.observe(this, Observer {
            if (it == null) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            runAsync {
                deviceEntry.waitForNullableValue()?.let { deviceEntry ->
                    binding.editText.setText(deviceEntry.name)
                    didInitField()
                }
            }
        }

        binding.title = getString(R.string.manage_device_rename)
    }

    override fun go() {
        val newDeviceTitle = binding.editText.text.toString()

        if (newDeviceTitle.isBlank()) {
            Toast.makeText(context!!, R.string.manage_device_rename_toast_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (auth.tryDispatchParentAction(
                            UpdateDeviceNameAction(
                                    deviceId = deviceId,
                                    name = newDeviceTitle
                            )
                    )) {
                dismiss()
            }
        }
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}
