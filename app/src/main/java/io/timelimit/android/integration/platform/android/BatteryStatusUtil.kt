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
package io.timelimit.android.integration.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.async.Threads
import io.timelimit.android.extensions.registerNotExportedReceiver
import io.timelimit.android.integration.platform.BatteryStatus

class BatteryStatusUtil(context: Context) {
    private val statusInternal = MutableLiveData<BatteryStatus>().apply { value = BatteryStatus.dummy }
    val status: LiveData<BatteryStatus> = statusInternal

    private fun handleIntent(intent: Intent) {
        val charging = run {
            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
        }

        val level = run {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            (level * 100 / scale).coerceIn(0, 100)
        }

        statusInternal.value = BatteryStatus(
            charging = charging,
            level = level
        )
    }

    init {
        context.applicationContext.registerNotExportedReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = handleIntent(intent)
        }, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.also {
            Threads.mainThreadHandler.post { handleIntent(it) }
        }
    }
}