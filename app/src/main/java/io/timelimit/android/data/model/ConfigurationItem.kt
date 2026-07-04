/*
 * Open TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.*
import io.timelimit.android.data.JsonSerializable

@Entity(tableName = "config")
@TypeConverters(ConfigurationItemTypeConverter::class)
data class ConfigurationItem(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val key: ConfigurationItemType,
        @ColumnInfo(name = "value")
        val value: String
): JsonSerializable {
    companion object {
        private const val KEY = "key"
        private const val VALUE = "value"

        // returns null if parsing failed
        fun parse(reader: JsonReader): ConfigurationItem? {
            var key: Int? = null
            var value: String? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    KEY -> key = reader.nextInt()
                    VALUE -> value = reader.nextString()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            key!!
            value!!

            try {
                return ConfigurationItem(
                        key = ConfigurationItemTypeUtil.parse(key),
                        value = value
                )
            } catch (ex: Exception) {
                return null
            }
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(KEY).value(ConfigurationItemTypeUtil.serialize(key))
        writer.name(VALUE).value(value)

        writer.endObject()
    }
}

enum class ConfigurationItemType {
    OwnDeviceId,
    ShownHints,
    WasDeviceLocked,
    ForegroundAppQueryRange,
    EnableAlternativeDurationSelection,
    LastScreenOnTime,
    ExperimentalFlags,
    DefaultHomescreen,
    HomescreenDelay,
    ParentModeKey,
    CustomOrganizationName,
    AnnoyManualUnblockCounter,
}

object ConfigurationItemTypeUtil {
    private const val OWN_DEVICE_ID = 1
    private const val SHOWN_HINTS = 2
    private const val WAS_DEVICE_LOCKED = 3
    private const val FOREGROUND_APP_QUERY_RANGE = 4
    private const val ENABLE_ALTERNATIVE_DURATION_SELECTION = 5
    private const val LAST_SCREEN_ON_TIME = 6
    private const val EXPERIMENTAL_FLAGS = 7
    private const val DEFAULT_HOMESCREEN = 8
    private const val HOMESCREEN_DELAY = 9
    private const val PARENT_MODE_KEY = 10
    private const val CUSTOM_ORGANIZATION_NAME = 11
    private const val ANNOY_MANUAL_UNBLOCK_COUNTER = 12

    val TYPES = listOf(
            ConfigurationItemType.OwnDeviceId,
            ConfigurationItemType.ShownHints,
            ConfigurationItemType.WasDeviceLocked,
            ConfigurationItemType.ForegroundAppQueryRange,
            ConfigurationItemType.EnableAlternativeDurationSelection,
            ConfigurationItemType.LastScreenOnTime,
            ConfigurationItemType.ExperimentalFlags,
            ConfigurationItemType.DefaultHomescreen,
            ConfigurationItemType.HomescreenDelay,
            ConfigurationItemType.ParentModeKey,
            ConfigurationItemType.CustomOrganizationName,
            ConfigurationItemType.AnnoyManualUnblockCounter
    )

    fun serialize(value: ConfigurationItemType) = when(value) {
        ConfigurationItemType.OwnDeviceId -> OWN_DEVICE_ID
        ConfigurationItemType.ShownHints -> SHOWN_HINTS
        ConfigurationItemType.WasDeviceLocked -> WAS_DEVICE_LOCKED
        ConfigurationItemType.ForegroundAppQueryRange -> FOREGROUND_APP_QUERY_RANGE
        ConfigurationItemType.EnableAlternativeDurationSelection -> ENABLE_ALTERNATIVE_DURATION_SELECTION
        ConfigurationItemType.LastScreenOnTime -> LAST_SCREEN_ON_TIME
        ConfigurationItemType.ExperimentalFlags -> EXPERIMENTAL_FLAGS
        ConfigurationItemType.DefaultHomescreen -> DEFAULT_HOMESCREEN
        ConfigurationItemType.HomescreenDelay -> HOMESCREEN_DELAY
        ConfigurationItemType.ParentModeKey -> PARENT_MODE_KEY
        ConfigurationItemType.CustomOrganizationName -> CUSTOM_ORGANIZATION_NAME
        ConfigurationItemType.AnnoyManualUnblockCounter -> ANNOY_MANUAL_UNBLOCK_COUNTER
    }

    fun parse(value: Int) = when(value) {
        OWN_DEVICE_ID -> ConfigurationItemType.OwnDeviceId
        SHOWN_HINTS -> ConfigurationItemType.ShownHints
        WAS_DEVICE_LOCKED -> ConfigurationItemType.WasDeviceLocked
        FOREGROUND_APP_QUERY_RANGE -> ConfigurationItemType.ForegroundAppQueryRange
        ENABLE_ALTERNATIVE_DURATION_SELECTION -> ConfigurationItemType.EnableAlternativeDurationSelection
        LAST_SCREEN_ON_TIME -> ConfigurationItemType.LastScreenOnTime
        EXPERIMENTAL_FLAGS -> ConfigurationItemType.ExperimentalFlags
        DEFAULT_HOMESCREEN -> ConfigurationItemType.DefaultHomescreen
        HOMESCREEN_DELAY -> ConfigurationItemType.HomescreenDelay
        PARENT_MODE_KEY -> ConfigurationItemType.ParentModeKey
        CUSTOM_ORGANIZATION_NAME -> ConfigurationItemType.CustomOrganizationName
        ANNOY_MANUAL_UNBLOCK_COUNTER -> ConfigurationItemType.AnnoyManualUnblockCounter
        else -> throw IllegalArgumentException()
    }
}

class ConfigurationItemTypeConverter {
    @TypeConverter
    fun toInt(value: ConfigurationItemType) = ConfigurationItemTypeUtil.serialize(value)

    @TypeConverter
    fun toConfigurationItemType(value: Int) = ConfigurationItemTypeUtil.parse(value)
}

object HintsToShow {
    const val OVERVIEW_INTRODUCTION = 1L
    const val DEVICE_SCREEN_INTRODUCTION = 2L
    const val CATEGORIES_INTRODUCTION = 4L
    const val TIME_LIMIT_RULE_INTRODUCTION = 8L
    const val CONTACTS_INTRO = 16L
    private const val OBSOLETE_TIMELIMIT_RULE_MUSTREAD = 32L
    const val BLOCKED_TIME_AREAS_OBSOLETE = 64L
    const val TASKS_INTRODUCTION = 128L
}

object ExperimentalFlags {
    private const val OBSOLETE_DISABLE_BLOCK_ON_MANIPULATION = 1L
    const val SYSTEM_LEVEL_BLOCKING = 2L
    private const val OBSOLETE_MANIPULATION_ANNOY_USER_ONLY = 4L
    const val CUSTOM_HOME_SCREEN = 8L
    const val CUSTOM_HOMESCREEN_DELAY = 16L
    const val NETWORKTIME_AT_SYSTEMLEVEL = 32L
    const val HIGH_MAIN_LOOP_DELAY = 64L
    const val MULTI_APP_DETECTION = 128L
    // const val BLOCK_SPLIT_SCREEN = 256L
    const val HIDE_MANIPULATION_WARNING = 512L
    const val ENABLE_SOFT_BLOCKING = 1024L
    // const val INSTANCE_ID_FG_APP_DETECTION = 2048L
    // private const val OBSOLETE_DISABLE_FG_APP_DETECTION_FALLBACK = 4096L
    const val STRICT_OVERLAY_CHECKING = 0x2000L
}
