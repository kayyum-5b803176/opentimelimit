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
package io.timelimit.android.ui.lowbattery

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.timelimit.android.integration.platform.android.AccessibilityService
import io.timelimit.android.integration.platform.android.LowBatteryBlockerSettings
import android.provider.Settings as AndroidSettings

/**
 * Minimal, self-contained settings screen for "block apps below X% battery".
 *
 * Built with plain views instead of the app's usual DataBinding + nav-graph setup
 * so it can be dropped in without touching the existing navigation XML. Wire it up
 * from anywhere with:
 *
 *   startActivity(Intent(context, LowBatteryBlockerSettingsActivity::class.java))
 */
class LowBatteryBlockerSettingsActivity: AppCompatActivity() {
    private lateinit var settings: LowBatteryBlockerSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = LowBatteryBlockerSettings.with(this)

        val padding = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val accessibilityHint = TextView(this).apply {
            text = if (AccessibilityService.instance == null)
                "Accessibility service is not enabled - foreground app detection won't work until it's turned on in system settings."
            else
                "Accessibility service is active."
        }
        root.addView(accessibilityHint)

        val openAccessibilitySettingsButton = android.widget.Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener {
                startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        root.addView(openAccessibilitySettingsButton)

        val enableSwitch = Switch(this).apply {
            text = "Block apps when battery is low"
            isChecked = settings.enabled.value ?: false
            setOnCheckedChangeListener { _, isChecked -> settings.setEnabled(isChecked) }
        }
        root.addView(enableSwitch)

        val thresholdLabel = TextView(this)
        root.addView(thresholdLabel)

        fun updateThresholdLabel(value: Int) {
            thresholdLabel.text = "Block below $value% battery (unless charging)"
        }

        val initialThreshold = settings.thresholdPercent.value ?: LowBatteryBlockerSettings.DEFAULT_THRESHOLD_PERCENT
        updateThresholdLabel(initialThreshold)

        val thresholdSeekBar = SeekBar(this).apply {
            max = 98 // maps to 1..99 in the listener below
            progress = (initialThreshold - 1).coerceIn(0, max)

            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 1

                    updateThresholdLabel(value)

                    if (fromUser) settings.setThresholdPercent(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(thresholdSeekBar)

        setContentView(root)
    }
}
