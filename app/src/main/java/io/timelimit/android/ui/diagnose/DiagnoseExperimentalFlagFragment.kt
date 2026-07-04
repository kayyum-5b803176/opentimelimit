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
package io.timelimit.android.ui.diagnose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.databinding.DiagnoseExperimentalFlagFragmentBinding
import io.timelimit.android.databinding.DiagnoseExperimentalFlagItemBinding
import io.timelimit.android.integration.platform.android.foregroundapp.LollipopForegroundAppHelper
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.homescreen.ConfigureHomescreenDelayDialogFragment
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class DiagnoseExperimentalFlagFragment : Fragment(), FragmentWithCustomTitle {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val activity: ActivityViewModelHolder = activity as ActivityViewModelHolder
        val database = DefaultAppLogic.with(requireContext()).database
        val auth = activity.getActivityViewModel()

        val binding = DiagnoseExperimentalFlagFragmentBinding.inflate(inflater, container, false)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                doesSupportAuth = liveDataFromNonNullValue(true),
                fragment = this
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        val flags = DiagnoseExperimentalFlagItem.items
        val checkboxes = flags.map { flag ->
            DiagnoseExperimentalFlagItemBinding.inflate(LayoutInflater.from(context), binding.container, true).apply {
                label = getString(flag.label)

                configButton.setOnClickListener {
                    if (auth.requestAuthenticationOrReturnTrue()) {
                        flag.configHook?.invoke(parentFragmentManager)
                    }
                }
            }
        }

        database.config().experimentalFlags.observe(viewLifecycleOwner, Observer { setFlags ->
            flags.forEachIndexed { index, flag ->
                val checkbox = checkboxes[index]
                val isFlagSet = (setFlags and flag.enableFlags) == flag.enableFlags

                checkbox.enabled = flag.enable(setFlags)
                checkbox.showConfigButton = isFlagSet && flag.configHook != null

                checkbox.checkbox.setOnCheckedChangeListener { _, _ -> }
                checkbox.checkbox.isChecked = isFlagSet
                checkbox.checkbox.setOnCheckedChangeListener { _, didCheck ->
                    if (didCheck != isFlagSet) {
                        if (auth.requestAuthenticationOrReturnTrue()) {
                            Threads.database.execute {
                                if (didCheck) {
                                    database.config().setExperimentalFlag(flag.enableFlags, true)

                                    flag.postEnableHook?.invoke(database)
                                } else {
                                    database.config().setExperimentalFlag(flag.disableFlags, false)
                                }
                            }
                        } else {
                            checkbox.checkbox.isChecked = isFlagSet
                        }
                    }
                }
            }
        })

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.diagnose_exf_title)} < ${getString(R.string.about_diagnose_title)} < ${getString(R.string.main_tab_overview)}")
}

data class DiagnoseExperimentalFlagItem(
        val label: Int,
        val enableFlags: Long,
        val disableFlags: Long,
        val enable: (flags: Long) -> Boolean,
        val postEnableHook: ((Database) -> Unit)? = null,
        val configHook: ((FragmentManager) -> Unit)? = null
) {
    companion object {
        val items = listOf(
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_slb,
                        enableFlags = ExperimentalFlags.SYSTEM_LEVEL_BLOCKING,
                        disableFlags = ExperimentalFlags.SYSTEM_LEVEL_BLOCKING,
                        enable = { true }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_nas,
                        enableFlags = ExperimentalFlags.NETWORKTIME_AT_SYSTEMLEVEL,
                        disableFlags = ExperimentalFlags.NETWORKTIME_AT_SYSTEMLEVEL,
                        enable = { true }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_chs,
                        enableFlags = ExperimentalFlags.CUSTOM_HOME_SCREEN,
                        disableFlags = ExperimentalFlags.CUSTOM_HOME_SCREEN or ExperimentalFlags.CUSTOM_HOMESCREEN_DELAY,
                        enable = { true },
                        postEnableHook = { it.config().setDefaultHomescreenSync(null) }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_chd,
                        enableFlags = ExperimentalFlags.CUSTOM_HOMESCREEN_DELAY,
                        disableFlags = ExperimentalFlags.CUSTOM_HOMESCREEN_DELAY,
                        enable = { flags -> flags and ExperimentalFlags.CUSTOM_HOME_SCREEN == ExperimentalFlags.CUSTOM_HOME_SCREEN },
                        configHook = { fragmentManager -> ConfigureHomescreenDelayDialogFragment().show(fragmentManager) }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_mld,
                        enableFlags = ExperimentalFlags.HIGH_MAIN_LOOP_DELAY,
                        disableFlags = ExperimentalFlags.HIGH_MAIN_LOOP_DELAY,
                        enable = { true }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_mad,
                        enableFlags = ExperimentalFlags.MULTI_APP_DETECTION,
                        disableFlags = ExperimentalFlags.MULTI_APP_DETECTION,
                        enable = { LollipopForegroundAppHelper.enableMultiAppDetectionGeneral }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_hmw,
                        enableFlags = ExperimentalFlags.HIDE_MANIPULATION_WARNING,
                        disableFlags = ExperimentalFlags.HIDE_MANIPULATION_WARNING,
                        enable = { true }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_esb,
                        enableFlags = ExperimentalFlags.ENABLE_SOFT_BLOCKING,
                        disableFlags = ExperimentalFlags.ENABLE_SOFT_BLOCKING,
                        enable = { true }
                ),
            DiagnoseExperimentalFlagItem(
                label = R.string.diagnose_exf_soc,
                enableFlags = ExperimentalFlags.STRICT_OVERLAY_CHECKING,
                disableFlags = ExperimentalFlags.STRICT_OVERLAY_CHECKING,
                enable = { true }
            )
        )
    }
}
