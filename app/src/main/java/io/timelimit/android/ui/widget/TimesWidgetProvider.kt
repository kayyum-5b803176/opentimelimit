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
package io.timelimit.android.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.integration.platform.android.BackgroundActionService
import io.timelimit.android.logic.DefaultAppLogic

class TimesWidgetProvider: AppWidgetProvider() {
    companion object {
        private const val LOG_TAG = "TimesWidgetProvider"

        private fun handleUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            runAsync {
                val configs = Threads.database.executeAndWait {
                    try {
                        DefaultAppLogic.with(context).database.widgetConfig()
                            .queryAll()
                            .associateBy { it.widgetId }
                    } catch (ex: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "could not query database", ex)
                        }

                        emptyMap()
                    }
                }

                for (appWidgetId in appWidgetIds) {
                    val config = configs[appWidgetId]
                    val translucent = config?.translucent ?: false

                    val views = RemoteViews(
                        context.packageName,
                        if (translucent) R.layout.widget_times_translucent
                        else R.layout.widget_times
                    )

                    views.setRemoteAdapter(android.R.id.list, TimesWidgetService.intent(context, appWidgetId, translucent))
                    views.setPendingIntentTemplate(android.R.id.list, BackgroundActionService.getSwitchToDefaultUserIntent(context))
                    views.setEmptyView(android.R.id.list, android.R.id.empty)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }

                TimesWidgetService.notifyContentChanges(context)
            }
        }

        fun triggerUpdates(context: Context, appWidgetIds: IntArray? = null) {
            context.getSystemService<AppWidgetManager>()?.also { appWidgetManager ->
                val usedAppWidgetIds = appWidgetIds
                    ?: appWidgetManager.getAppWidgetIds(ComponentName(context, TimesWidgetProvider::class.java))

                handleUpdate(context, appWidgetManager, usedAppWidgetIds)
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
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "onDisabled", ex)
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        val database = DefaultAppLogic.with(context).database

        Threads.database.execute {
            try {
                database.runInTransaction {
                    database.widgetCategory().deleteAll()
                    database.widgetConfig().deleteAll()
                }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "onDisabled", ex)
                }
            }
        }
    }
}