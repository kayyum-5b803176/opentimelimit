/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
import android.view.LayoutInflater
import android.widget.Button
import android.widget.HorizontalScrollView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import io.timelimit.android.R

class KeyboardView(context: Context, attributeSet: AttributeSet): HorizontalScrollView(context, attributeSet) {
    companion object {
        private const val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789.,-"
    }

    var listener: KeyboardViewListener? = null

    private val flexbox = FlexboxLayout(context)
    private val inflater = LayoutInflater.from(context)

    init {
        flexbox.flexDirection = FlexDirection.COLUMN
        flexbox.flexWrap = FlexWrap.WRAP

        addView(flexbox)

        chars.forEach { char ->
            inflater.inflate(R.layout.keyboard_btn, flexbox, false).let { button ->
                button as Button

                button.apply {
                    transformationMethod = null
                    text = char.toString()
                    minWidth = 0
                    minHeight = 0

                    setOnClickListener {
                        listener?.onItemClicked(char.toString())
                    }
                }

                flexbox.addView(button)
            }
        }

        inflater.inflate(R.layout.keyboard_btn, flexbox, false).let { button ->
            button as Button

            button.apply {
                setText(R.string.generic_go)

                setOnClickListener {
                    listener?.onGoClicked()
                }
            }

            flexbox.addView(button)
        }
    }
}

interface KeyboardViewListener {
    fun onItemClicked(content: String)
    fun onGoClicked()
}