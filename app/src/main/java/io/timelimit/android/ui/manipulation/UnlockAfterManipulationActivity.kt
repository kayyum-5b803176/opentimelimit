/*
 * Open TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
package io.timelimit.android.ui.manipulation

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.ActivityUnlockAfterManipulationBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.backdoor.BackdoorDialogFragment
import io.timelimit.android.ui.login.NewLoginFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder

class UnlockAfterManipulationActivity : AppCompatActivity(), ActivityViewModelHolder {
    private val model: ActivityViewModel by lazy {
        ViewModelProviders.of(this).get(ActivityViewModel::class.java)
    }

    override var ignoreStop: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityUnlockAfterManipulationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask()
        } else {
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        model.authenticatedUser.observe(this, Observer {
            user ->

            if (user != null && user.type == UserType.Parent) {
                onAuthSuccess()
            }
        })

        model.logic.deviceId.observe(this, Observer {
            if (it.isNullOrEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    stopLockTask()
                }

                DefaultAppLogic.with(this).appSetupLogic.resetAppCompletely()

                finish()
            }
        })

        binding.authBtn.setOnClickListener { showAuthenticationScreen() }
        binding.useBackdoor.setOnClickListener { BackdoorDialogFragment().show(supportFragmentManager) }
    }

    override fun showAuthenticationScreen() {
        NewLoginFragment().apply {
            arguments = Bundle().apply {
                putBoolean(NewLoginFragment.SHOW_ON_LOCKSCREEN, true)
            }
        }.showSafe(supportFragmentManager, "nlf")
    }

    override fun getActivityViewModel() = model

    private fun onAuthSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
        }

        val appLogic = DefaultAppLogic.with(this@UnlockAfterManipulationActivity)

        Threads.database.execute {
            appLogic.manipulationLogic.unlockDeviceSync()
        }

        appLogic.manipulationLogic.showManipulationUnlockedScreen()

        finish()
    }
}
