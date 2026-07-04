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

import com.google.zxing.common.BitMatrix
import java.util.*

data class BarcodeMask(val width: Int, val height: Int, val mask: BitSet) {
    companion object {
        fun fromBitMatrix(bitMatrix: BitMatrix) = BarcodeMask(
                width = bitMatrix.width,
                height = bitMatrix.height,
                mask = BitSet(bitMatrix.width * bitMatrix.height).apply {
                    (0 until bitMatrix.width).forEach { x ->
                        (0 until bitMatrix.height).forEach { y ->
                            set(y * bitMatrix.width + x, bitMatrix[x, y])
                        }
                    }
                }
        )
    }

    fun withPadding(size: Int) = BarcodeMask(
            width = width + size * 2,
            height = height + size * 2,
            mask = BitSet((width + size * 2) * (height + size * 2)).apply {
                (0 until width).forEach { x ->
                    (0 until height).forEach { y ->
                        set((y + size) * (width + size * 2) + (x + size), mask[y * width + x])
                    }
                }
            }
    )
}