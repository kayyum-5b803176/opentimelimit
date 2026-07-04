/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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

import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.model.App
import io.timelimit.android.databinding.AppFilterViewBinding

object AppFilterView {
    fun getFilter(view: AppFilterViewBinding): AppFilter = buildAppFilter(
            searchTerm = view.search.text.toString(),
            includeSystemApps = view.showSystemApps.isChecked
    )

    fun getFilterLive(view: AppFilterViewBinding): LiveData<AppFilter> {
        val result = MutableLiveData<AppFilter>().apply { value = getFilter(view) }

        fun update() {
            result.value = getFilter(view)
        }

        view.showSystemApps.setOnCheckedChangeListener { _, _ -> update() }
        view.search.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // ignore
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // ignore
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                update()
            }
        })

        return result
    }

    private fun buildAppFilter(
            searchTerm: String,
            includeSystemApps: Boolean
    ): AppFilter {
        return object: AppFilter {
            override fun matches(app: App): Boolean {
                if ((!includeSystemApps) && (!app.isLaunchable)) {
                    return false
                }

                if (searchTerm.isNotBlank()) {
                    if (app.title.contains(searchTerm, true) || app.packageName.contains(searchTerm, true)) {
                        // ok
                    } else {
                        return false
                    }
                }

                return true
            }
        }
    }

    interface AppFilter {
        fun matches(app: App): Boolean

        companion object {
            val dummy = object: AppFilter {
                override fun matches(app: App) = true
            }
        }
    }
}
