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

package io.timelimit.android.data.model.derived

import io.timelimit.android.data.Database
import io.timelimit.android.data.invalidation.Observer
import io.timelimit.android.data.invalidation.Table
import io.timelimit.android.data.model.Device
import java.lang.ref.WeakReference

data class DeviceRelatedData (
        val deviceEntry: Device,
        val hasValidDefaultUser: Boolean,
        val temporarilyAllowedApps: Set<String>,
        val experimentalFlags: Long
): Observer {
    companion object {
        private val relatedTables = arrayOf(Table.ConfigurationItem, Table.Device, Table.User, Table.TemporarilyAllowedApp)

        fun load(database: Database): DeviceRelatedData? = database.runInUnobservedTransaction {
            val deviceId = database.config().getOwnDeviceIdSync() ?: return@runInUnobservedTransaction null
            val deviceEntry = database.device().getDeviceByIdSync(deviceId) ?: return@runInUnobservedTransaction null
            val hasValidDefaultUser = database.user().getUserByIdSync(deviceEntry.defaultUser) != null
            val temporarilyAllowedApps = database.temporarilyAllowedApp().getTemporarilyAllowedAppsSync().toSet()
            val experimentalFlags = database.config().getExperimentalFlagsSync()

            DeviceRelatedData(
                    deviceEntry = deviceEntry,
                    hasValidDefaultUser = hasValidDefaultUser,
                    temporarilyAllowedApps = temporarilyAllowedApps,
                    experimentalFlags = experimentalFlags
            ).also {
                database.registerWeakObserver(relatedTables, WeakReference(it))
            }
        }
    }

    val canSwitchToDefaultUser = hasValidDefaultUser && deviceEntry.currentUserId != deviceEntry.defaultUser

    private var invalidated = false

    override fun onInvalidated(tables: Set<Table>) { invalidated = true }

    fun update(database: Database): DeviceRelatedData? {
        if (!invalidated) {
            return this
        }

        return load(database)
    }

    fun isExperimentalFlagSetSync(flags: Long) = (experimentalFlags and flags) == flags
}