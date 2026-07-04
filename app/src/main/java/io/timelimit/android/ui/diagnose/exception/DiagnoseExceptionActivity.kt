/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.diagnose.exception

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.timelimit.android.logic.DefaultAppLogic

class DiagnoseExceptionActivity: FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ex = DefaultAppLogic.with(this).backgroundTaskLogic.lastLoopException.value

        if (ex != null) {
            if (savedInstanceState == null) {
                DiagnoseExceptionDialogFragment.newInstance(ex, true).show(supportFragmentManager)
            }
        } else finish()
    }
}