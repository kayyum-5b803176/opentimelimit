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
package io.timelimit.android.ui.manage.category.settings.timewarning

import android.os.Parcelable
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryTimeWarning
import io.timelimit.android.data.model.CategoryTimeWarnings
import io.timelimit.android.sync.actions.UpdateCategoryTimeWarningsAction
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class CategoryTimeWarningStatus(
    private val categoryFlags: Int?,
    private val timeWarnings: Set<Int>?,
    private val additionalTimeWarningSlots: Set<Int>
): Parcelable {
    companion object {
        val default = CategoryTimeWarningStatus(
            categoryFlags = null,
            timeWarnings = null,
            additionalTimeWarningSlots = emptySet()
        )
    }

    fun update(category: Category): CategoryTimeWarningStatus {
        if (this.categoryFlags == category.timeWarnings) return this

        return this.copy(categoryFlags = category.timeWarnings)
    }

    fun update(warnings: List<CategoryTimeWarning>): CategoryTimeWarningStatus {
        val timeWarnings = warnings.map { it.minutes }.toSet()

        if (this.timeWarnings == timeWarnings) return this

        return this.copy(
            timeWarnings = timeWarnings,
            additionalTimeWarningSlots = additionalTimeWarningSlots + timeWarnings
        )
    }

    fun buildAction(categoryId: String, minutes: Int, enable: Boolean): UpdateCategoryTimeWarningsAction {
        val flagIndex = CategoryTimeWarnings.durationInMinutesToBitIndex[minutes]

        return if (enable) {
            if (flagIndex != null) UpdateCategoryTimeWarningsAction(
                categoryId = categoryId,
                enable = true,
                flags = 1 shl flagIndex,
                minutes = null
            ) else UpdateCategoryTimeWarningsAction(
                categoryId = categoryId,
                enable = true,
                flags = 0,
                minutes = minutes
            )
        } else {
            UpdateCategoryTimeWarningsAction(
                categoryId = categoryId,
                enable = false,
                flags = if (flagIndex != null) 1 shl flagIndex else 0,
                minutes = if (timeWarnings != null && timeWarnings.contains(minutes)) minutes else null
            )
        }
    }

    @IgnoredOnParcel
    val display = TreeMap<Int, CategoryTimeWarningOptionStatus>().also { result ->
        val complete = categoryFlags != null && timeWarnings != null

        additionalTimeWarningSlots.forEach { minute ->
            result[minute] = if (complete) CategoryTimeWarningOptionStatus.Unchecked
            else CategoryTimeWarningOptionStatus.Undefined
        }

        timeWarnings?.forEach { minute -> result[minute] = CategoryTimeWarningOptionStatus.Checked }

        CategoryTimeWarnings.durationInMinutesToBitIndex.forEach { (minute, bitIndex) ->
            result[minute] = if (complete && categoryFlags != null) {
                if (categoryFlags and (1 shl bitIndex) != 0) CategoryTimeWarningOptionStatus.Checked
                else CategoryTimeWarningOptionStatus.Unchecked
            } else CategoryTimeWarningOptionStatus.Undefined
        }
    }.let { output ->
        mutableListOf<CategoryTimeWarningOption>().also { result ->
            output.entries.forEach { (minute, status) ->
                result.add(CategoryTimeWarningOption(minute, status))
            }
        }
    }.toList()

    data class CategoryTimeWarningOption(
        val minutes: Int,
        val status: CategoryTimeWarningOptionStatus
    )

    enum class CategoryTimeWarningOptionStatus {
        Checked,
        Unchecked,
        Undefined
    }
}