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
package io.timelimit.android.data.devicename

import android.content.Context
import android.util.Log
import io.timelimit.android.BuildConfig
import java.util.zip.InflaterInputStream

class DeviceNameDatabase (private val data: ByteArray) {
    companion object {
        private const val LOG_TAG = "DeviceNameDatabase"

        fun fromAssets(context: Context): DeviceNameDatabase {
            val data = context.assets.open("device-names.bin").use { stream ->
                InflaterInputStream(stream).use { decompressed ->
                    decompressed.readBytes()
                }
            }

            return DeviceNameDatabase(data)
        }
    }

    private val stringBufferLength = read4(0)
    private val deviceCounter = read4(4)
    private val deviceDataStartIndex = stringBufferLength + 8

    private fun read1(index: Int): Int = data[index].toInt() and 0xff

    private fun read3(index: Int) = (read1(index) shl 16) or
            (read1(index + 1) shl 8) or
            read1(index + 2)

    private fun read4(index: Int) = (read1(index) shl 24) or
            (read1(index + 1) shl 16) or
            (read1(index + 2) shl 8) or
            read1(index + 3)

    private fun readString(index: Int): String {
        return String(data, read3(index) + 8, read1(index + 3), Charsets.UTF_8)
    }

    private fun getDeviceName(index: Int): String = readString(deviceDataStartIndex + index * 12 + 0)
    private fun getDeviceModel(index: Int): String = readString(deviceDataStartIndex + index * 12 + 4)
    private fun getDeviceMarketingName(index: Int): String = readString(deviceDataStartIndex + index * 12 + 8)

    fun getDeviceMarketingName(device: String, model: String): String? {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "search ($device, $model)")
        }

        var low = 0
        var high = deviceCounter - 1

        while (low <= high) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "low = $low; high = $high")
            }

            val index = low + (high - low) / 2

            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "compare with (${getDeviceName(index)}, ${getDeviceModel(index)}, ${getDeviceMarketingName(index)})")
            }

            val cmp1 = getDeviceName(index).compareTo(device)

            if (cmp1 < 0) low = index + 1
            else if (cmp1 > 0) high = index - 1
            else {
                val cmp2 = getDeviceModel(index).compareTo(model)

                if (cmp2 < 0) low = index + 1
                else if (cmp2 > 0) high = index - 1
                else {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "got match")
                    }

                    return getDeviceMarketingName(index)
                }
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "nothing found")
        }

        return null
    }
}