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
package io.timelimit.android.ui.extensions

import android.widget.ViewFlipper
import io.timelimit.android.R

fun ViewFlipper.openNextWizardScreen(index: Int) {
    if (displayedChild != index) {
        setInAnimation(context, R.anim.wizard_open_step_in)
        setOutAnimation(context, R.anim.wizard_open_step_out)
        displayedChild = index
    }
}

fun ViewFlipper.openPreviousWizardScreen(index: Int) {
    if (displayedChild != index) {
        setInAnimation(context, R.anim.wizard_close_step_in)
        setOutAnimation(context, R.anim.wizard_close_step_out)
        displayedChild = index
    }
}