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
package io.timelimit.android.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.UserType
import io.timelimit.android.data.model.WidgetCategory
import io.timelimit.android.data.model.WidgetConfig
import io.timelimit.android.logic.DefaultAppLogic

/**
 * Configuration screen for the one row/one category widget. Deliberately minimal:
 * pick exactly ONE category (radio buttons) plus the translucent background
 * option, then save. Built with plain views like LowBatteryBlockerSettingsActivity
 * so it doesn't touch the existing DataBinding/nav-graph setup.
 */
class SingleTimeWidgetConfigActivity: AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // if the user cancels, the widget host must not place the widget
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()

            return
        }

        title = getString(R.string.widget_single_config_title)

        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        })

        val logic = DefaultAppLogic.with(applicationContext)

        runAsync {
            val loaded = Threads.database.executeAndWait {
                try {
                    val database = logic.database
                    val derived = database.derivedDataDao().getUserAndDeviceRelatedDataSync()
                    val userRelatedData = derived?.userRelatedData

                    val categories = if (userRelatedData?.user?.type == UserType.Child)
                        userRelatedData.sortedCategories().map { pair ->
                            Triple(pair.first, pair.second.category.id, pair.second.category.title)
                        }
                    else emptyList()

                    val selected = database.widgetCategory().queryByWidgetIdSync(appWidgetId).toSet()
                    val translucent = database.widgetConfig().queryByWidgetId(appWidgetId)?.translucent ?: false

                    Triple(categories, selected, translucent)
                } catch (ex: Exception) {
                    Triple(emptyList(), emptySet(), false)
                }
            }

            val (categories, selectedIds, translucent) = loaded

            if (categories.isEmpty()) {
                container.addView(TextView(this@SingleTimeWidgetConfigActivity).apply {
                    text = getString(R.string.widget_msg_no_category)
                })

                return@runAsync
            }

            container.addView(TextView(this@SingleTimeWidgetConfigActivity).apply {
                text = getString(R.string.widget_single_config_hint)
            })

            val radioGroup = RadioGroup(this@SingleTimeWidgetConfigActivity)
            val radioIdToCategoryId = mutableMapOf<Int, String>()

            categories.forEachIndexed { index, (level, categoryId, categoryTitle) ->
                val button = RadioButton(this@SingleTimeWidgetConfigActivity).apply {
                    id = index + 1
                    text = categoryTitle
                    setPadding((level * 24 * density).toInt(), 0, 0, 0)
                    isChecked = selectedIds.contains(categoryId) || (selectedIds.isEmpty() && index == 0)
                }

                radioIdToCategoryId[button.id] = categoryId
                radioGroup.addView(button)
            }

            container.addView(radioGroup)

            val translucentSwitch = Switch(this@SingleTimeWidgetConfigActivity).apply {
                text = getString(R.string.widget_config_other_translucent)
                isChecked = translucent
            }

            container.addView(translucentSwitch)

            container.addView(Button(this@SingleTimeWidgetConfigActivity).apply {
                text = getString(R.string.generic_save)

                setOnClickListener {
                    val categoryId = radioIdToCategoryId[radioGroup.checkedRadioButtonId] ?: return@setOnClickListener
                    val isTranslucent = translucentSwitch.isChecked

                    runAsync {
                        Threads.database.executeAndWait {
                            logic.database.runInTransaction {
                                logic.database.widgetCategory().deleteByWidgetId(appWidgetId)
                                logic.database.widgetCategory().insert(listOf(WidgetCategory(appWidgetId, categoryId)))
                                logic.database.widgetConfig().upsert(WidgetConfig(appWidgetId, isTranslucent))
                            }
                        }

                        SingleTimeWidgetProvider.triggerUpdates(this@SingleTimeWidgetConfigActivity, intArrayOf(appWidgetId))

                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    }
                }
            })
        }
    }
}
