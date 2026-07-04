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
package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import io.timelimit.android.data.model.Device
import io.timelimit.android.integration.platform.NewPermissionStatusConverter
import io.timelimit.android.integration.platform.ProtectionLevelConverter
import io.timelimit.android.integration.platform.RuntimePermissionStatusConverter

@Dao
@TypeConverters(
        ProtectionLevelConverter::class,
        RuntimePermissionStatusConverter::class,
        NewPermissionStatusConverter::class
)
abstract class DeviceDao {
    @Query("SELECT * FROM device WHERE id = :deviceId")
    abstract fun getDeviceById(deviceId: String): LiveData<Device?>

    @Query("SELECT * FROM device WHERE id = :deviceId")
    abstract fun getDeviceByIdSync(deviceId: String): Device?

    @Query("SELECT * FROM device")
    abstract fun getAllDevicesLive(): LiveData<List<Device>>

    @Query("SELECT * FROM device")
    abstract fun getAllDevicesSync(): List<Device>

    @Insert
    abstract fun addDeviceSync(device: Device)

    @Query("UPDATE device SET current_user_id = :userId WHERE id = :deviceId")
    abstract fun updateDeviceUser(deviceId: String, userId: String)

    @Query("UPDATE device SET default_user = :defaultUserId WHERE id = :deviceId")
    abstract fun updateDeviceDefaultUser(deviceId: String, defaultUserId: String)

    @Update
    abstract fun updateDeviceEntry(device: Device)

    @Query("SELECT * FROM device WHERE current_user_id = :userId")
    abstract fun getDevicesByUserId(userId: String): LiveData<List<Device>>

    @Query("UPDATE device SET name = :name WHERE id = :deviceId")
    abstract fun updateDeviceName(deviceId: String, name: String): Int

    @Query("SELECT * FROM device LIMIT :pageSize OFFSET :offset")
    abstract fun getDevicePageSync(offset: Int, pageSize: Int): List<Device>

    @Query("UPDATE device SET current_user_id = \"\" WHERE current_user_id = :userId")
    abstract fun unassignCurrentUserFromAllDevices(userId: String)
}
