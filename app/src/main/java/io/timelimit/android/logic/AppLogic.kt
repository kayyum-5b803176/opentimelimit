/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.logic

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.Device
import io.timelimit.android.data.model.User
import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.integration.time.TimeApi
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.ui.widget.TimesWidgetProvider

class AppLogic(
        val platformIntegration: PlatformIntegration,
        val timeApi: TimeApi,
        val database: Database,
        val context: Context,
        val isInitialized: LiveData<Boolean>
) {
    val enable = MutableLiveData<Boolean>().apply { value = true }

    val deviceId = database.config().getOwnDeviceId()

    val deviceEntry = deviceId.switchMap<String?, Device?> {
        if (it == null) {
            liveDataFromNullableValue(null)
        } else {
            database.device().getDeviceById(it)
        }
    }.ignoreUnchanged()

    val deviceEntryIfEnabled = enable.switchMap {
        if (it == null || it == false) {
            liveDataFromNullableValue(null as Device?)
        } else {
            deviceEntry
        }
    }

    val deviceUserId: LiveData<String> = deviceEntry.map { it?.currentUserId ?: "" }

    val deviceUserEntry = deviceUserId.switchMap {
        if (it == "") {
            liveDataFromNullableValue(null as User?)
        } else {
            database.user().getUserByIdLive(it)
        }
    }.ignoreUnchanged()

    private val foregroundAppQueryInterval = database.config().getForegroundAppQueryIntervalAsync().apply { observeForever {  } }

    fun getForegroundAppQueryInterval() = foregroundAppQueryInterval.value ?: 0L

    val defaultUserLogic = DefaultUserLogic(this)
    val realTimeLogic = RealTimeLogic(this)
    val backgroundTaskLogic = BackgroundTaskLogic(this)
    val appSetupLogic = AppSetupLogic(this)

    init {
        SyncInstalledAppsLogic(this)
        WatchdogLogic(this)
        TimesWidgetProvider.triggerUpdates(context)
        io.timelimit.android.ui.widget.SingleTimeWidgetProvider.triggerUpdates(context)
    }

    val manipulationLogic = ManipulationLogic(this)
    val suspendAppsLogic = SuspendAppsLogic(this)
    val annoyLogic = AnnoyLogic(this)

    fun shutdown() {
        enable.value = false
    }
}
