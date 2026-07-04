/*
 * Open TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.databinding.FragmentSetupLocalModeBinding
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.mustread.MustReadFragment
import io.timelimit.android.ui.view.NotifyPermissionCard

class SetupLocalModeFragment : Fragment() {
    companion object {
        private const val STATUS_NOTIFY_PERMISSION = "notify permission"
    }

    private val model: SetupLocalModeModel by viewModels()
    private var notifyPermission = MutableLiveData<NotifyPermissionCard.Status>()

    private val requestNotifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) notifyPermission.value = NotifyPermissionCard.Status.Granted
        else Toast.makeText(requireContext(), R.string.notify_permission_rejected_toast, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            notifyPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                savedInstanceState.getSerializable(STATUS_NOTIFY_PERMISSION, NotifyPermissionCard.Status::class.java)!!
            else
                savedInstanceState.getSerializable(STATUS_NOTIFY_PERMISSION)!! as NotifyPermissionCard.Status
        }

        notifyPermission.value = NotifyPermissionCard.updateStatus(notifyPermission.value ?: NotifyPermissionCard.Status.Unknown, requireContext())
    }

    override fun onResume() {
        super.onResume()

        notifyPermission.value = NotifyPermissionCard.updateStatus(notifyPermission.value ?: NotifyPermissionCard.Status.Unknown, requireContext())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(STATUS_NOTIFY_PERMISSION, notifyPermission.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupLocalModeBinding.inflate(inflater, container, false)

        mergeLiveDataWaitForValues(binding.setPasswordView.passwordOk, model.status, notifyPermission)
            .observe(viewLifecycleOwner) { (passwordGood, modelStatus, notifyPermission) ->
                val isIdle = modelStatus == SetupLocalModeModel.Status.Idle

                binding.setPasswordView.isEnabled = isIdle

                binding.nextBtn.isEnabled = passwordGood && isIdle && NotifyPermissionCard.canProceed(notifyPermission)
            }

        model.status.observe(viewLifecycleOwner) {
            val isIdle = it == SetupLocalModeModel.Status.Idle

            binding.setPasswordView.isEnabled = isIdle
        }

        model.status.observe(this, Observer {
            if (it == SetupLocalModeModel.Status.Done) {
                MustReadFragment.newInstance(R.string.must_read_child_manipulation).show(fragmentManager!!)
            }
        })

        binding.nextBtn.setOnClickListener {
            model.trySetupWithPassword(
                    binding.setPasswordView.readPassword()
            )
        }

        binding.setPasswordView.allowNoPassword.value = true

        NotifyPermissionCard.bind(object: NotifyPermissionCard.Listener {
            override fun onGrantClicked() { requestNotifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            override fun onSkipClicked() { notifyPermission.value = NotifyPermissionCard.Status.SkipGrant }
        }, binding.notifyPermissionCard)

        notifyPermission.observe(viewLifecycleOwner) { NotifyPermissionCard.bind(it, binding.notifyPermissionCard) }

        return binding.root
    }
}

class SetupLocalModeModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "SetupLocalModeModel"
    }

    enum class Status {
        Idle, Running, Done
    }

    val status = MutableLiveData<Status>()

    init {
        status.value = Status.Idle
    }

    fun trySetupWithPassword(parentPassword: String) {
        runAsync {
            if (status.value != Status.Idle) {
                throw IllegalStateException()
            }

            status.value = Status.Running

            try {
                DefaultAppLogic.with(getApplication()).appSetupLogic.setupForLocalUse(parentPassword, getApplication())
                status.value = Status.Done
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "setup failed", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                status.value = Status.Idle
            }
        }
    }
}
