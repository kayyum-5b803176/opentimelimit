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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.livedata.mergeLiveDataWaitForValues
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.child.category.CategoryItemLeftPadding

class TimesWidgetService: RemoteViewsService() {
    companion object {
        private const val EXTRA_APP_WIDGET_ID = "appWidgetId"
        private const val EXTRA_TRANSLUCENT = "translucent"

        fun intent(context: Context, appWidgetId: Int, translucent: Boolean) = Intent(context, TimesWidgetService::class.java)
            .setData(Uri.parse("widget:$appWidgetId:$translucent"))
            .putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
            .putExtra(EXTRA_TRANSLUCENT, translucent)

        fun notifyContentChanges(context: Context) {
            context.getSystemService<AppWidgetManager>()?.also { appWidgetManager ->
                val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, TimesWidgetProvider::class.java))

                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, android.R.id.list)
            }
        }
    }

    private val content: LiveData<Pair<TimesWidgetContent, TimesWidgetConfig>> by lazy {
        val logic = DefaultAppLogic.with(this)

        val content = TimesWidgetContentLoader.with(logic)
        val config = logic.database.widgetCategory().queryLive().map { TimesWidgetConfig(it) }

        mergeLiveDataWaitForValues(content, config)
    }

    private var observerCounter = 0
    private var contentInput: Pair<TimesWidgetContent, TimesWidgetConfig>? = null

    private val contentObserver = Observer<Pair<TimesWidgetContent, TimesWidgetConfig>> {
        contentInput = it

        notifyContentChanges(this)
    }

    private fun createFactory(appWidgetId: Int, translucent: Boolean) = object : RemoteViewsFactory {
        private var currentItems: List<TimesWidgetItem> = emptyList()

        init { onDataSetChanged() }

        override fun onCreate() {
            Threads.mainThreadHandler.post {
                if (observerCounter < 0) throw IllegalStateException()
                else if (observerCounter == 0) content.observeForever(contentObserver)

                observerCounter++
            }
        }

        override fun onDestroy() {
            Threads.mainThreadHandler.post {
                if (observerCounter <= 0) throw IllegalStateException()
                else if (observerCounter == 1) content.removeObserver(contentObserver)

                observerCounter--
            }
        }

        override fun onDataSetChanged() {
            currentItems = contentInput?.let { TimesWidgetItems.with(it.first, it.second, appWidgetId) } ?: emptyList()
        }

        override fun getCount(): Int = currentItems.size

        override fun getViewAt(position: Int): RemoteViews {
            val categoryItemView = if (translucent) R.layout.widget_times_category_item_translucent
            else R.layout.widget_times_category_item

            if (position >= currentItems.size) {
                return RemoteViews(packageName, categoryItemView)
            }

            fun createCategoryItem(title: String?, subtitle: String, paddingLeft: Int) = RemoteViews(packageName, categoryItemView).also { result ->
                result.setTextViewText(R.id.title, title ?: "")
                result.setTextViewText(R.id.subtitle, subtitle)

                result.setViewPadding(R.id.widgetInnerContainer, paddingLeft, 0, 0, 0)
                result.setViewVisibility(R.id.title, if (title != null) View.VISIBLE else View.GONE)
                result.setViewVisibility(R.id.topPadding, if (position == 0) View.VISIBLE else View.GONE)
                result.setViewVisibility(R.id.bottomPadding, if (position == count - 1) View.VISIBLE else View.GONE)
            }

            val item = currentItems[position]

            return when (item) {
                is TimesWidgetItem.Category -> item.category.let { category ->
                    createCategoryItem(
                        title = if (category.remainingTimeToday == null)
                            getString(R.string.manage_child_category_no_time_limits_short)
                        else {
                            val remainingTimeToday = category.remainingTimeToday.coerceAtLeast(0) / (1000 * 60)
                            val minutes = remainingTimeToday % 60
                            val hours = remainingTimeToday / 60

                            if (hours == 0L) "$minutes m"
                            else "$hours h $minutes m"
                        },
                        subtitle = category.categoryName,
                        // not much space here => / 2
                        paddingLeft = CategoryItemLeftPadding.calculate(category.level, this@TimesWidgetService) / 2
                    )
                }
                is TimesWidgetItem.TextMessage -> createCategoryItem(null, getString(item.textRessourceId), 0)
                is TimesWidgetItem.DefaultUserButton -> RemoteViews(packageName, R.layout.widget_times_button).also { result ->
                    result.setTextViewText(R.id.button, getString(R.string.manage_device_default_user_switch_btn))
                    result.setOnClickFillInIntent(R.id.button, Intent())
                }
            }
        }

        override fun getLoadingView(): RemoteViews?  = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long {
            if (position >= currentItems.size) {
                return -(position.toLong())
            }

            val item = currentItems[position]

            return when (item) {
                is TimesWidgetItem.Category -> item.category.categoryId.hashCode()
                else -> item.hashCode()
            }.toLong()
        }

        override fun hasStableIds(): Boolean = true
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = createFactory(
        intent.getIntExtra(EXTRA_APP_WIDGET_ID, 0),
        intent.getBooleanExtra(EXTRA_TRANSLUCENT, false)
    )
}