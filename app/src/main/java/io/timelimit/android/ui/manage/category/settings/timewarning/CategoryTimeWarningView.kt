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

import android.widget.CheckBox
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import io.timelimit.android.R
import io.timelimit.android.databinding.CategoryTimeWarningsViewBinding
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.util.TimeTextUtil

object CategoryTimeWarningView {
    fun bind(
        view: CategoryTimeWarningsViewBinding,
        lifecycleOwner: LifecycleOwner,
        statusLive: LiveData<CategoryTimeWarningStatus>,
        auth: ActivityViewModel,
        fragmentManager: FragmentManager,
        categoryId: String
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                title = R.string.time_warning_title,
                text = R.string.time_warning_desc
            ).show(fragmentManager)
        }

        view.addTimeWarningButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                AddTimeWarningDialogFragment.newInstance(categoryId).show(fragmentManager)
            }
        }

        view.linearLayout.removeAllViews()

        val views = mutableListOf<CheckBox>()

        statusLive.observe(lifecycleOwner) { status ->
            if (views.size != status.display.size) {
                views.clear()
                view.linearLayout.removeAllViews()

                for (index in 1..status.display.size) {
                    CheckBox(view.root.context).also { checkbox ->
                        views.add(checkbox)
                        view.linearLayout.addView(checkbox)
                    }
                }
            }

            status.display.forEachIndexed { index, item ->
                val checkbox = views[index]

                val enabled = item.status != CategoryTimeWarningStatus.CategoryTimeWarningOptionStatus.Undefined
                val checked = item.status == CategoryTimeWarningStatus.CategoryTimeWarningOptionStatus.Checked

                checkbox.text = TimeTextUtil.time(item.minutes * 1000 * 60, view.root.context)

                checkbox.setOnCheckedChangeListener(null)

                checkbox.isEnabled = enabled
                checkbox.isChecked = checked

                if (item.status != CategoryTimeWarningStatus.CategoryTimeWarningOptionStatus.Undefined) {
                    checkbox.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked != checked) {
                            if (auth.tryDispatchParentAction(status.buildAction(
                                    categoryId = categoryId,
                                    minutes = item.minutes,
                                    enable = isChecked
                            ))) {
                                // it worked
                            } else {
                                checkbox.isChecked = checked
                            }
                        }
                    }
                }
            }
        }
    }
}