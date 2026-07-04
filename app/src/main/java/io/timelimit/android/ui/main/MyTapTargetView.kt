/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView

@SuppressLint("ViewConstructor")
class MyTapTargetView(
        context: Context, parent: ViewManager, boundingParent: ViewGroup?, target: TapTarget?, val userListener: Listener?
): TapTargetView(context, parent, boundingParent, target, userListener) {
    companion object {
        fun showFor(activity: Activity, target: TapTarget?, listener: Listener?): TapTargetView? {
            val decor = activity.window.decorView as ViewGroup
            val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val content = decor.findViewById<View>(android.R.id.content) as ViewGroup
            val tapTargetView = MyTapTargetView(activity, decor, content, target, listener)

            decor.addView(tapTargetView, layoutParams)

            return tapTargetView
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVisible && (
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
        ) {
            userListener?.onTargetClick(this)

            return true
        } else {
            return super.onKeyDown(keyCode, event)
        }
    }
}