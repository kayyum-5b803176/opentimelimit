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
package io.timelimit.android.ui.homescreen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.timelimit.android.R
import io.timelimit.android.databinding.ActivityHomescreenBinding
import io.timelimit.android.databinding.ActivityHomescreenItemBinding

class HomescreenActivity: AppCompatActivity() {
    companion object {
        private const val FORCE_SELECTION = "forceSelection"
    }

    private val model: HomescreenModel by lazy { ViewModelProvider(this).get(HomescreenModel::class.java) }
    private lateinit var binding: ActivityHomescreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomescreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        model.handleLaunchIfNotYetExecuted(intent.getBooleanExtra(FORCE_SELECTION, false))

        model.status.observe(this, Observer { status ->
            when (status) {
                SelectionListHomescreenStatus -> {
                    hideProgress()

                    initOptionList()
                }
                is TryLaunchHomescreenStatus -> {
                    hideOptionList()
                    hideProgress()

                    if (!status.didTry) {
                        status.didTry = true

                        try {
                            startActivity(HomescreenUtil.openHomescreenIntent().setComponent(status.component))
                        } catch (ex: ActivityNotFoundException) {
                            model.showSelectionList()
                        }
                    }
                }
                is DelayHomescreenStatus -> {
                    hideOptionList()

                    showProgress(status.progress)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        model.handleLaunch(intent?.getBooleanExtra(FORCE_SELECTION, false) ?: false)
    }

    private fun initOptionList() {
        binding.maincard.visibility = View.VISIBLE

        val options = HomescreenUtil.launcherOptions(this)

        binding.launcherOptions.removeAllViews()

        options.forEach { option ->
            val view = ActivityHomescreenItemBinding.inflate(LayoutInflater.from(this), binding.launcherOptions, true)

            view.label = try {
                packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(option.packageName, 0)
                ).toString()
            } catch (ex: PackageManager.NameNotFoundException) {
                null
            } ?: option.packageName

            try {
                view.icon.setImageDrawable(packageManager.getApplicationIcon(option.packageName))
            } catch (ex: Exception) {
                view.icon.setImageResource(R.mipmap.ic_launcher_round)
            }

            view.root.setOnClickListener {
                try {
                    startActivity(
                            HomescreenUtil.openHomescreenIntent().setComponent(option)
                    )

                    hideOptionList()
                    model.saveDefaultOption(option)
                } catch (ex: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun hideOptionList() {
        binding.maincard.visibility = View.GONE

        binding.launcherOptions.removeAllViews()
    }

    private fun showProgress(progresss: Int) {
        binding.progressCard.visibility = View.VISIBLE
        binding.progressBar.progress = progresss
    }

    private fun hideProgress() {
        binding.progressCard.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        model.handleResume()
    }

    override fun onPause() {
        super.onPause()

        model.handlePause()
    }
}