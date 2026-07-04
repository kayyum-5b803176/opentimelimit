/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.timelimit.android.R
import io.timelimit.android.databinding.ViewManageDisableTimeLimitsBinding
import kotlin.properties.Delegates

class ManageDisableTimelimitsView(context: Context, attributeSet: AttributeSet): FrameLayout(context, attributeSet) {
    private val binding = ViewManageDisableTimeLimitsBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        addView(binding.root)
    }

    var disableTimeLimitsUntilString: String? by Delegates.observable(null as String?) {
        _, _, value ->

        if (TextUtils.isEmpty(value)) {
            binding.disabledUntilString = null
        } else {
            binding.disabledUntilString = resources.getString(R.string.manage_disable_time_limits_info_enabled, value)
        }
    }

    var handlers: ManageDisableTimelimitsViewHandlers? by Delegates.observable(null as ManageDisableTimelimitsViewHandlers?) {
        _, _, value -> binding.handlers = value
    }

    init {
        binding.titleView.setOnClickListener { handlers?.showDisableTimeLimitsHelp() }
    }
}

interface ManageDisableTimelimitsViewHandlers {
    fun disableTimeLimitsUntilSelectedTimeOfToday()
    fun disableTimeLimitsUntilSelectedDate()
    fun disableTimeLimitsForDuration(duration: Long)
    fun disableTimeLimitsForToday()
    fun enableTimeLimits()
    fun showDisableTimeLimitsHelp()
}
