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
package io.timelimit.android.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.databinding.FragmentSetupSelectModeBinding
import io.timelimit.android.extensions.safeNavigate
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.setup.parentmode.SetupParentModeDeviceOwnerDialogFragment
import io.timelimit.android.ui.setup.parentmode.SetupParentmodeDialogFragment

class SetupSelectModeFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SetupSelectModeFragment"
        private const val REQUEST_SETUP_PARENT_MODE = 1
    }

    private lateinit var navigation: NavController
    private lateinit var binding: FragmentSetupSelectModeBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSetupSelectModeBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext().applicationContext
        val logic = DefaultAppLogic.with(requireContext())

        navigation = Navigation.findNavController(view)

        binding.btnChildMode.setOnClickListener {
            navigation.safeNavigate(
                    SetupSelectModeFragmentDirections.actionSetupSelectModeFragmentToSetupDevicePermissionsFragment(),
                    R.id.setupSelectModeFragment
            )
        }

        binding.btnParentMode.setOnClickListener {
            if (logic.platformIntegration.getCurrentProtectionLevel() == ProtectionLevel.DeviceOwner) {
                SetupParentModeDeviceOwnerDialogFragment.newInstance().show(childFragmentManager)
            } else {
                SetupParentmodeDialogFragment().apply {
                    setTargetFragment(this@SetupSelectModeFragment, REQUEST_SETUP_PARENT_MODE)
                }.show(parentFragmentManager)
            }
        }

        binding.btnUninstall.setOnClickListener {
            runAsync {
                try {
                    Threads.database.executeAndWait { SetupUnprovisionedCheck.checkSync(logic.database) }

                    logic.platformIntegration.disableDeviceAdmin()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${requireContext().packageName}")
                            )
                                .addCategory(Intent.CATEGORY_DEFAULT)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_UNINSTALL_PACKAGE,
                                Uri.parse("package:${requireContext().packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "reset failed", ex)
                    }

                    Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}