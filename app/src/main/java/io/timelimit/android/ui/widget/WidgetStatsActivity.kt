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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.timelimit.android.R
import io.timelimit.android.integration.platform.android.PendingIntentIds
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.child.category.CategoryItemLeftPadding
import io.timelimit.android.util.TimeTextUtil

/**
 * Read-only "remaining time" overview which opens when the user taps the one row
 * widget (SingleTimeWidgetProvider). Deliberately contains no configuration
 * actions - it just answers the question "how much time is left?" in a big,
 * readable form and updates live while it's visible (same data source as the
 * widgets themselves).
 *
 * Built with plain views (like LowBatteryBlockerSettingsActivity) so it can be
 * dropped in without touching the existing DataBinding/nav-graph setup.
 */
class WidgetStatsActivity: AppCompatActivity() {
    companion object {
        fun getPendingIntentTemplate(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            PendingIntentIds.WIDGET_STATS,
            Intent(context, WidgetStatsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntentIds.PENDING_INTENT_FLAGS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.widget_stats_title)

        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(list)
        })

        fun addMessage(text: String) {
            list.addView(TextView(this).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, padding, 0, padding)
            })
        }

        fun addCategoryRow(item: TimesWidgetContent.Categories.Item) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(CategoryItemLeftPadding.calculate(item.level, this@WidgetStatsActivity), padding / 2, 0, padding / 2)
            }

            row.addView(TextView(this).apply {
                text = if (item.remainingTimeToday == null)
                    getString(R.string.manage_child_category_no_time_limits_short)
                else
                    TimeTextUtil.remaining(item.remainingTimeToday.coerceAtLeast(0).toInt(), this@WidgetStatsActivity)

                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(typeface, Typeface.BOLD)
            })

            row.addView(TextView(this).apply {
                text = item.categoryName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })

            list.addView(row)
        }

        val logic = DefaultAppLogic.with(applicationContext)

        // same live data source the widget uses: refreshes on database changes and
        // re-schedules itself for the next moment a displayed value flips over
        TimesWidgetContentLoader.with(logic).observe(this) { content ->
            list.removeAllViews()

            when (content) {
                is TimesWidgetContent.UnconfiguredDevice -> addMessage(getString(R.string.widget_msg_unconfigured))
                is TimesWidgetContent.NoChildUser -> addMessage(getString(R.string.widget_msg_no_child))
                is TimesWidgetContent.Categories -> {
                    if (content.categories.isEmpty()) {
                        addMessage(getString(R.string.widget_msg_no_category))
                    } else {
                        content.categories.forEach { addCategoryRow(it) }
                    }
                }
            }
        }
    }
}
