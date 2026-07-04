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
package io.timelimit.android.ui.widget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe

class WidgetConfigActivity: FragmentActivity() {
    private val model: WidgetConfigModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (model.state.value == WidgetConfigModel.State.WaitingForInit) {
            model.init(
                intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    ?: AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        model.state.observe(this) { state ->
            when (state) {
                is WidgetConfigModel.State.WaitingForInit -> {/* ignore */}
                is WidgetConfigModel.State.Working -> {/* ignore */}
                is WidgetConfigModel.State.Unconfigured -> {
                    if (supportFragmentManager.findFragmentByTag(UnconfiguredDialogFragment.DIALOG_TAG) == null) {
                        UnconfiguredDialogFragment().showSafe(supportFragmentManager, UnconfiguredDialogFragment.DIALOG_TAG)
                    }
                }
                is WidgetConfigModel.State.ShowModeSelection -> {
                    if (supportFragmentManager.findFragmentByTag(WidgetConfigModeDialogFragment.DIALOG_TAG) == null) {
                        WidgetConfigModeDialogFragment().showSafe(supportFragmentManager, WidgetConfigModeDialogFragment.DIALOG_TAG)
                    }
                }
                is WidgetConfigModel.State.ShowCategorySelection -> {
                    if (supportFragmentManager.findFragmentByTag(WidgetConfigFilterDialogFragment.DIALOG_TAG) == null) {
                        WidgetConfigFilterDialogFragment().showSafe(supportFragmentManager, WidgetConfigFilterDialogFragment.DIALOG_TAG)
                    }
                }
                is WidgetConfigModel.State.ShowOtherOptions -> {
                    if (supportFragmentManager.findFragmentByTag(WidgetConfigOtherDialogFragment.DIALOG_TAG) == null) {
                        WidgetConfigOtherDialogFragment().showSafe(supportFragmentManager, WidgetConfigOtherDialogFragment.DIALOG_TAG)
                    }
                }
                is WidgetConfigModel.State.Done -> {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, state.appWidgetId)
                    )

                    finish()
                }
                is WidgetConfigModel.State.ErrorCancel -> {
                    Toast.makeText(this, R.string.error_general, Toast.LENGTH_SHORT).show()

                    finish()
                }
                is WidgetConfigModel.State.UserCancel -> finish()
            }
        }

        setResult(RESULT_CANCELED)
    }
}