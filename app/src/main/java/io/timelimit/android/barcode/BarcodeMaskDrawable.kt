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
package io.timelimit.android.barcode

import android.graphics.*
import android.graphics.drawable.Drawable

class BarcodeMaskDrawable(val mask: BarcodeMask): Drawable() {
    private val blackPaint = Paint(Color.BLACK)

    override fun draw(target: Canvas) {
        target.drawColor(Color.WHITE)

        val bLeft = bounds.left
        val bTop = bounds.top
        val bWidth = bounds.width()
        val bHeight = bounds.height()

        (0 until mask.height).forEach { y ->
            (0 until mask.width).forEach { x ->
                if (mask.mask[y * mask.width + x]) {
                    val left = bLeft + x.toFloat() / mask.width * bWidth
                    val top = bTop + y.toFloat() / mask.height * bHeight
                    val right = bLeft + (x + 1).toFloat() / mask.width * bWidth
                    val bottom = bTop + (y + 1).toFloat() / mask.height * bHeight

                    target.drawRect(
                            left,
                            top,
                            right,
                            bottom,
                            blackPaint
                    )
                }
            }
        }
    }

    override fun setAlpha(p0: Int) {
        // ignored
    }

    override fun setColorFilter(p0: ColorFilter?) {
        // ignored
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE

    override fun getIntrinsicWidth(): Int = mask.width * 2

    override fun getIntrinsicHeight(): Int = mask.height * 2
}