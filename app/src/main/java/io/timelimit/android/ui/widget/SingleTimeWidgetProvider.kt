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
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.UserType
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.blockingreason.CategoryHandlingCache

/**
 * One row, one category widget (Digital Wellbeing style): shows the remaining time
 * of a single selected category as a flat view (no ListView involved, so the text
 * always renders at its full natural size). Tapping it opens the read-only
 * remaining time overview (WidgetStatsActivity).
 *
 * The category is selected in SingleTimeWidgetConfigActivity and stored in the
 * existing widget_category table (widget ids are unique across providers, so the
 * table can be shared with the classic list widget). When nothing was selected,
 * the first category of the current child user is shown.
 */
class SingleTimeWidgetProvider: AppWidgetProvider() {
    companion object {
        private const val LOG_TAG = "SingleTimeWidget"

        // Keeps placed widgets live: as a flat widget there is no RemoteViewsFactory
        // observing the content, so this observer (registered only while at least one
        // widget instance exists) refreshes them whenever the shown values change.
        // It reuses the same content loader as the list widget which already handles
        // database changes, clock boundaries and time modifications.
        private var refreshLive: LiveData<TimesWidgetContent>? = null
        private var refreshObserver: Observer<TimesWidgetContent>? = null

        private fun widgetIds(context: Context, appWidgetManager: AppWidgetManager): IntArray =
            appWidgetManager.getAppWidgetIds(ComponentName(context, SingleTimeWidgetProvider::class.java))

        // must be called at the main thread
        private fun syncRefreshObserver(context: Context, appWidgetManager: AppWidgetManager) {
            val appContext = context.applicationContext
            val anyWidget = widgetIds(appContext, appWidgetManager).isNotEmpty()

            if (anyWidget && refreshLive == null) {
                val live = TimesWidgetContentLoader.with(DefaultAppLogic.with(appContext))

                val observer = Observer<TimesWidgetContent> {
                    appContext.getSystemService<AppWidgetManager>()?.also { manager ->
                        val ids = widgetIds(appContext, manager)

                        if (ids.isNotEmpty()) handleUpdate(appContext, manager, ids)
                    }
                }

                refreshLive = live
                refreshObserver = observer

                live.observeForever(observer)
            } else if (!anyWidget && refreshLive != null) {
                refreshObserver?.let { refreshLive?.removeObserver(it) }
                refreshLive = null
                refreshObserver = null
            }
        }

        private fun handleUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            runAsync {
                val logic = DefaultAppLogic.with(context)

                for (appWidgetId in appWidgetIds) {
                    appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, logic, appWidgetId))
                }

                syncRefreshObserver(context, appWidgetManager)
            }
        }

        private suspend fun buildViews(context: Context, logic: AppLogic, appWidgetId: Int): RemoteViews {
            data class Content(
                val translucent: Boolean,
                val category: TimesWidgetContent.Categories.Item?,
                val emptyMessage: Int
            )

            val content = Threads.database.executeAndWait {
                try {
                    val database = logic.database
                    val translucent = database.widgetConfig().queryByWidgetId(appWidgetId)?.translucent ?: false
                    val derived = database.derivedDataDao().getUserAndDeviceRelatedDataSync()
                    val userRelatedData = derived?.userRelatedData

                    if (derived == null)
                        return@executeAndWait Content(translucent, null, R.string.widget_msg_unconfigured)
                    if (userRelatedData?.user?.type != UserType.Child)
                        return@executeAndWait Content(translucent, null, R.string.widget_msg_no_child)

                    val cache = CategoryHandlingCache()

                    cache.reportStatus(
                        user = userRelatedData,
                        timeInMillis = logic.timeApi.getCurrentTimeInMillis(),
                        batteryStatus = logic.platformIntegration.getBatteryStatus(),
                        currentNetworkId = null
                    )

                    val categories = userRelatedData.sortedCategories().map { pair ->
                        val level = pair.first
                        val category = pair.second
                        val handling = cache.get(categoryId = category.category.id)

                        TimesWidgetContent.Categories.Item(
                            categoryId = category.category.id,
                            categoryName = category.category.title,
                            level = level,
                            remainingTimeToday = handling.remainingTime?.includingExtraTime
                        )
                    }

                    val selectedIds = database.widgetCategory().queryByWidgetIdSync(appWidgetId).toSet()
                    val category = categories.firstOrNull { selectedIds.contains(it.categoryId) }
                        ?: categories.firstOrNull()

                    Content(translucent, category, R.string.widget_msg_no_category)
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) Log.d(LOG_TAG, "buildViews failed", ex)

                    Content(translucent = false, category = null, emptyMessage = R.string.widget_msg_unconfigured)
                }
            }

            val layout = if (content.translucent) R.layout.widget_single_time_translucent else R.layout.widget_single_time
            val views = RemoteViews(context.packageName, layout)

            val category = content.category

            if (category == null) {
                views.setTextViewText(R.id.widgetSingleLabel, context.getString(R.string.widget_stats_title))
                views.setTextViewText(R.id.widgetSingleTime, context.getString(content.emptyMessage))
            } else {
                views.setTextViewText(R.id.widgetSingleLabel, category.categoryName)
                views.setTextViewText(
                    R.id.widgetSingleTime,
                    if (category.remainingTimeToday == null)
                        context.getString(R.string.manage_child_category_no_time_limits_short)
                    else {
                        val mins = category.remainingTimeToday.coerceAtLeast(0) / (1000 * 60)
                        val h = mins / 60; val m = mins % 60

                        if (h == 0L) "$m m" else "$h h $m m"
                    }
                )
            }

            // tapping the widget opens the read-only remaining time overview
            views.setOnClickPendingIntent(R.id.widgetSingleRoot, WidgetStatsActivity.getPendingIntentTemplate(context))

            return views
        }

        fun triggerUpdates(context: Context, appWidgetIds: IntArray? = null) {
            context.getSystemService<AppWidgetManager>()?.also { appWidgetManager ->
                val usedIds = appWidgetIds ?: widgetIds(context, appWidgetManager)

                handleUpdate(context, appWidgetManager, usedIds)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)

        // always start the app logic
        DefaultAppLogic.with(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        handleUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        val database = DefaultAppLogic.with(context).database

        Threads.database.execute {
            try {
                database.runInTransaction {
                    database.widgetCategory().deleteByWidgetIds(appWidgetIds)
                    database.widgetConfig().deleteByWidgetIds(appWidgetIds)
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) Log.d(LOG_TAG, "onDeleted", ex)
            }
        }

        // eventually detach the refresh observer if this was the last widget instance
        triggerUpdates(context)
    }
}
