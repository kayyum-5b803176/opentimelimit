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

import io.timelimit.android.R

object TimesWidgetItems {
    fun with(content: TimesWidgetContent, config: TimesWidgetConfig, appWidgetId: Int): List<TimesWidgetItem> = when (content) {
        is TimesWidgetContent.UnconfiguredDevice -> listOf(TimesWidgetItem.TextMessage(R.string.widget_msg_unconfigured))
        is TimesWidgetContent.NoChildUser -> {
            val base = TimesWidgetItem.TextMessage(R.string.widget_msg_no_child)

            if (content.canSwitchToDefaultUser) listOf(base, TimesWidgetItem.DefaultUserButton)
            else listOf(base)
        }
        is TimesWidgetContent.Categories -> {
            val categoryFilter = config.widgetCategoriesByWidgetId[appWidgetId] ?: emptySet()

            val categoryItems = if (content.categories.isEmpty()) listOf(TimesWidgetItem.TextMessage(R.string.widget_msg_no_category))
            else if (categoryFilter.isEmpty()) content.categories.map { TimesWidgetItem.Category(it) }
            else {
                val filteredCategories = content.categories.filter { categoryFilter.contains(it.categoryId) }

                if (filteredCategories.isEmpty()) listOf(TimesWidgetItem.TextMessage(R.string.widget_msg_no_filtered_category))
                else filteredCategories.map { TimesWidgetItem.Category(it) }
            }

            if (content.canSwitchToDefaultUser) categoryItems + TimesWidgetItem.DefaultUserButton
            else categoryItems
        }
    }
}