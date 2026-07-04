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

import android.content.Context
import android.util.AttributeSet
import android.widget.ViewFlipper

// based on https://stackoverflow.com/a/8208874
// this should fix some rare crashes at Android 4.4
class SafeViewFlipper: ViewFlipper {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
        } catch (ex: IllegalArgumentException) {
            stopFlipping()
        }
    }
}