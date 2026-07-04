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
package io.timelimit.android.data.dao

import android.content.ComponentName
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.*
import io.timelimit.android.data.model.ConfigurationItem
import io.timelimit.android.data.model.ConfigurationItemType
import io.timelimit.android.data.model.ConfigurationItemTypeConverter
import io.timelimit.android.data.model.ConfigurationItemTypeUtil
import io.timelimit.android.livedata.ignoreUnchanged

@Dao
@TypeConverters(ConfigurationItemTypeConverter::class)
abstract class ConfigDao {
    @Query("SELECT * FROM config WHERE id IN (:keys)")
    protected abstract fun getConfigItemsSync(keys: List<Int>): List<ConfigurationItem>

    fun getConfigItemsSync() = getConfigItemsSync(ConfigurationItemTypeUtil.TYPES.map { ConfigurationItemTypeUtil.serialize(it) })

    @Query("SELECT * FROM config WHERE id = :key")
    protected abstract fun getRowByKeyAsync(key: ConfigurationItemType): LiveData<ConfigurationItem?>

    private fun getValueOfKeyAsync(key: ConfigurationItemType): LiveData<String?> {
        return getRowByKeyAsync(key).map { it?.value }.ignoreUnchanged()
    }

    @Query("SELECT * FROM config WHERE id = :key")
    protected abstract fun getRowByKeySync(key: ConfigurationItemType): ConfigurationItem?

    private fun getValueOfKeySync(key: ConfigurationItemType): String? {
        return getRowByKeySync(key)?.value
    }

    @Query("SELECT * FROM config WHERE id = :key")
    protected abstract suspend fun getRowCoroutine(key: ConfigurationItemType): ConfigurationItem?

    private suspend fun getValueOfKeyCoroutine(key: ConfigurationItemType): String? {
        return getRowCoroutine(key)?.value
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun updateValueOfKeySync(item: ConfigurationItem)

    @Query("DELETE FROM config WHERE id = :key")
    protected abstract fun removeConfigItemSync(key: ConfigurationItemType)

    private fun updateValueSync(key: ConfigurationItemType, value: String?) {
        if (value != null) {
            updateValueOfKeySync(ConfigurationItem(key, value))
        } else {
            removeConfigItemSync(key)
        }
    }

    fun getOwnDeviceId(): LiveData<String?> {
        return getValueOfKeyAsync(ConfigurationItemType.OwnDeviceId)
    }

    fun getOwnDeviceIdSync(): String? {
        return getValueOfKeySync(ConfigurationItemType.OwnDeviceId)
    }

    fun setOwnDeviceIdSync(deviceId: String) {
        updateValueSync(ConfigurationItemType.OwnDeviceId, deviceId)
    }

    private fun getShownHintsLive(): LiveData<Long> {
        return getValueOfKeyAsync(ConfigurationItemType.ShownHints).map {
            if (it == null) {
                0
            } else {
                it.toLong(16)
            }
        }
    }

    private fun getShownHintsSync(): Long {
        val v = getValueOfKeySync(ConfigurationItemType.ShownHints)

        if (v == null) {
            return 0
        } else {
            return v.toLong(16)
        }
    }

    fun wereHintsShown(flags: Long) = getShownHintsLive().map {
        (it and flags) == flags
    }.ignoreUnchanged()

    fun wereAnyHintsShown() = getShownHintsLive().map { it != 0L }.ignoreUnchanged()

    fun setHintsShownSync(flags: Long) {
        updateValueSync(
                ConfigurationItemType.ShownHints,
                (getShownHintsSync() or flags).toString(16)
        )
    }

    fun resetShownHintsSync() {
        updateValueSync(ConfigurationItemType.ShownHints, null)
    }

    fun wasDeviceLockedSync() = getValueOfKeySync(ConfigurationItemType.WasDeviceLocked) == "true"
    fun setWasDeviceLockedSync(value: Boolean) = updateValueSync(ConfigurationItemType.WasDeviceLocked, if (value) "true" else "false")

    fun getForegroundAppQueryIntervalAsync(): LiveData<Long> = getValueOfKeyAsync(ConfigurationItemType.ForegroundAppQueryRange).map { (it ?: "0").toLong() }
    fun setForegroundAppQueryIntervalSync(interval: Long) {
        if (interval < 0) {
            throw IllegalArgumentException()
        }

        updateValueSync(ConfigurationItemType.ForegroundAppQueryRange, interval.toString())
    }

    fun getEnableAlternativeDurationSelectionAsync() = getValueOfKeyAsync(ConfigurationItemType.EnableAlternativeDurationSelection).map { it == "1" }
    fun setEnableAlternativeDurationSelectionSync(enable: Boolean) = updateValueSync(ConfigurationItemType.EnableAlternativeDurationSelection, if (enable) "1" else "0")

    fun setLastScreenOnTime(time: Long) = updateValueSync(ConfigurationItemType.LastScreenOnTime, time.toString())
    fun getLastScreenOnTime() = getValueOfKeySync(ConfigurationItemType.LastScreenOnTime)?.toLong() ?: 0L

    protected fun getExperimentalFlagsLive(): LiveData<Long> {
        return getValueOfKeyAsync(ConfigurationItemType.ExperimentalFlags).map {
            if (it == null) {
                0
            } else {
                it.toLong(16)
            }
        }
    }

    val experimentalFlags: LiveData<Long> by lazy { getExperimentalFlagsLive() }

    fun getExperimentalFlagsSync(): Long {
        val v = getValueOfKeySync(ConfigurationItemType.ExperimentalFlags)

        if (v == null) {
            return 0
        } else {
            return v.toLong(16)
        }
    }

    fun isExperimentalFlagsSetAsync(flags: Long) = experimentalFlags.map {
        (it and flags) == flags
    }.ignoreUnchanged()

    fun isExperimentalFlagsSetSync(flags: Long) = (getExperimentalFlagsSync() and flags) == flags

    fun setExperimentalFlag(flags: Long, enable: Boolean) {
        updateValueSync(
                ConfigurationItemType.ExperimentalFlags,
                if (enable)
                    (getExperimentalFlagsSync() or flags).toString(16)
                else
                    (getExperimentalFlagsSync() and (flags.inv())).toString(16)
        )
    }

    fun getDefaultHomescreenSync(): ComponentName? = getValueOfKeySync(ConfigurationItemType.DefaultHomescreen)?.let {
        ComponentName.unflattenFromString(it)
    }

    fun setDefaultHomescreenSync(componentName: ComponentName?) = updateValueSync(ConfigurationItemType.DefaultHomescreen, componentName?.flattenToString())

    fun getHomescreenDelaySync(): Int = getValueOfKeySync(ConfigurationItemType.HomescreenDelay)?.toIntOrNull(radix = 10) ?: 5
    fun setHomescreenDelaySync(delay: Int) = updateValueSync(ConfigurationItemType.HomescreenDelay, delay.toString(radix = 10))

    fun setParentModeKeySync(key: ByteArray) = updateValueSync(ConfigurationItemType.ParentModeKey, Base64.encodeToString(key, 0))
    fun getParentModeKeySync() = getValueOfKeySync(ConfigurationItemType.ParentModeKey)?.let { value -> Base64.decode(value, 0) }
    fun getParentModeKeyLive() = getValueOfKeyAsync(ConfigurationItemType.ParentModeKey).map { value -> value?.let { Base64.decode(it, 0) } }

    suspend fun getCustomOrganizationName(): String = getValueOfKeyCoroutine(ConfigurationItemType.CustomOrganizationName) ?: ""
    fun setCustomOrganizationName(value: String) = updateValueSync(ConfigurationItemType.CustomOrganizationName, value)

    fun getAnnoyManualUnblockCounter() = getValueOfKeySync(ConfigurationItemType.AnnoyManualUnblockCounter).let { it?.toInt() ?: 0 }
    fun setAnoyManualUnblockCounterSync(counter: Int) { updateValueSync(ConfigurationItemType.AnnoyManualUnblockCounter, counter.toString()) }
}
