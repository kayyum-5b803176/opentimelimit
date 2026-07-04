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
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskAdapter
import io.timelimit.android.data.customtypes.ImmutableBitmaskJson
import java.util.*

@Entity(tableName = "category")
@TypeConverters(ImmutableBitmaskAdapter::class)
data class Category(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "child_id")
        val childId: String,
        @ColumnInfo(name = "title")
        val title: String,
        @ColumnInfo(name = "blocked_times")
        val blockedMinutesInWeek: ImmutableBitmask,    // 10080 bit -> ~10 KB
        @ColumnInfo(name = "extra_time")
        val extraTimeInMillis: Long,
        @ColumnInfo(name = "extra_time_day")
        val extraTimeDay: Int,
        @ColumnInfo(name = "temporarily_blocked")
        val temporarilyBlocked: Boolean,
        @ColumnInfo(name = "temporarily_blocked_end_time")
        val temporarilyBlockedEndTime: Long,
        @ColumnInfo(name = "parent_category_id")
        val parentCategoryId: String,
        @ColumnInfo(name = "block_all_notifications")
        val blockAllNotifications: Boolean,
        @ColumnInfo(name = "time_warnings")
        val timeWarnings: Int,
        @ColumnInfo(name = "min_battery_charging")
        val minBatteryLevelWhileCharging: Int,
        @ColumnInfo(name = "min_battery_mobile")
        val minBatteryLevelMobile: Int,
        @ColumnInfo(name = "sort")
        val sort: Int,
        // 0 = time limits enabled
        @ColumnInfo(name = "disable_limits_until")
        val disableLimitsUntil: Long,
        @ColumnInfo(name = "flags", defaultValue = "0")
        val flags: Long,
        @ColumnInfo(name = "block_notification_delay", defaultValue = "0")
        val blockNotificationDelay: Long
): JsonSerializable {
    companion object {
        const val MINUTES_PER_DAY = 60 * 24
        const val BLOCKED_MINUTES_IN_WEEK_LENGTH = MINUTES_PER_DAY * 7
        const val MAX_NOTIFICATION_BLOCK_DELAY = 1000 * 30L

        private const val ID = "id"
        private const val CHILD_ID = "childId"
        private const val TITLE = "title"
        private const val BLOCKED_MINUTES_IN_WEEK = "blockedMinutesInWeek"
        private const val EXTRA_TIME_IN_MILLIS = "extraTimeInMillis"
        private const val TEMPORARILY_BLOCKED = "temporarilyBlocked"
        private const val TEMPORARILY_BLOCKED_END_TIME = "temporarilyBlockedEndTime"
        private const val PARENT_CATEGORY_ID = "parentCategoryId"
        private const val BlOCK_ALL_NOTIFICATIONS = "blockAllNotifications"
        private const val TIME_WARNINGS = "timeWarnings"
        private const val MIN_BATTERY_CHARGING = "minBatteryCharging"
        private const val MIN_BATTERY_MOBILE = "minBatteryMobile"
        private const val SORT = "sort"
        private const val EXTRA_TIME_DAY = "extraTimeDay"
        private const val DISABLE_LIMIITS_UNTIL = "dlu"
        private const val FLAGS = "flags"
        private const val BLOCK_NOTIFICATION_DELAY = "bnd"

        fun parse(reader: JsonReader): Category {
            var id: String? = null
            var childId: String? = null
            var title: String? = null
            var blockedMinutesInWeek: ImmutableBitmask? = null
            var extraTimeInMillis: Long? = null
            var temporarilyBlocked: Boolean? = null
            var temporarilyBlockedEndTime: Long = 0
            // this field was added later so it has got a default value
            var parentCategoryId = ""
            var blockAllNotifications = false
            var timeWarnings = 0
            var minBatteryCharging = 0
            var minBatteryMobile = 0
            var sort = 0
            var extraTimeDay = -1
            var disableLimitsUntil = 0L
            var flags = 0L
            var blockNotificationDelay = 0L

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextString()
                    CHILD_ID -> childId = reader.nextString()
                    TITLE -> title = reader.nextString()
                    BLOCKED_MINUTES_IN_WEEK -> blockedMinutesInWeek = ImmutableBitmaskJson.parse(reader.nextString(), BLOCKED_MINUTES_IN_WEEK_LENGTH)
                    EXTRA_TIME_IN_MILLIS -> extraTimeInMillis = reader.nextLong()
                    TEMPORARILY_BLOCKED -> temporarilyBlocked = reader.nextBoolean()
                    TEMPORARILY_BLOCKED_END_TIME -> temporarilyBlockedEndTime = reader.nextLong()
                    PARENT_CATEGORY_ID -> parentCategoryId = reader.nextString()
                    BlOCK_ALL_NOTIFICATIONS -> blockAllNotifications = reader.nextBoolean()
                    TIME_WARNINGS -> timeWarnings = reader.nextInt()
                    MIN_BATTERY_CHARGING -> minBatteryCharging = reader.nextInt()
                    MIN_BATTERY_MOBILE -> minBatteryMobile = reader.nextInt()
                    SORT -> sort = reader.nextInt()
                    EXTRA_TIME_DAY -> extraTimeDay = reader.nextInt()
                    DISABLE_LIMIITS_UNTIL -> disableLimitsUntil = reader.nextLong()
                    FLAGS -> flags = reader.nextLong()
                    BLOCK_NOTIFICATION_DELAY -> blockNotificationDelay = reader.nextLong()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return Category(
                    id = id!!,
                    childId = childId!!,
                    title = title!!,
                    blockedMinutesInWeek = blockedMinutesInWeek!!,
                    extraTimeInMillis = extraTimeInMillis!!,
                    temporarilyBlocked = temporarilyBlocked!!,
                    temporarilyBlockedEndTime = temporarilyBlockedEndTime,
                    parentCategoryId = parentCategoryId,
                    blockAllNotifications = blockAllNotifications,
                    timeWarnings = timeWarnings,
                    minBatteryLevelWhileCharging = minBatteryCharging,
                    minBatteryLevelMobile = minBatteryMobile,
                    sort = sort,
                    extraTimeDay = extraTimeDay,
                    disableLimitsUntil = disableLimitsUntil,
                    flags = flags,
                    blockNotificationDelay = blockNotificationDelay
            )
        }
    }

    init {
        IdGenerator.assertIdValid(id)
        IdGenerator.assertIdValid(childId)

        if (extraTimeInMillis < 0) {
            throw IllegalStateException()
        }

        if (title.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (minBatteryLevelMobile < 0 || minBatteryLevelWhileCharging < 0) {
            throw IllegalArgumentException()
        }

        if (minBatteryLevelMobile > 100 || minBatteryLevelWhileCharging > 100) {
            throw IllegalArgumentException()
        }

        if (extraTimeDay < -1) {
            throw IllegalArgumentException()
        }

        if (disableLimitsUntil < 0) {
            throw IllegalArgumentException()
        }

        if (blockNotificationDelay < 0) {
            throw IllegalArgumentException()
        }
    }

    val hasBlockedNetworkList get() = flags and CategoryFlags.HAS_BLOCKED_NETWROK_LIST == CategoryFlags.HAS_BLOCKED_NETWROK_LIST

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(CHILD_ID).value(childId)
        writer.name(TITLE).value(title)
        writer.name(BLOCKED_MINUTES_IN_WEEK).value(ImmutableBitmaskJson.serialize(blockedMinutesInWeek))
        writer.name(EXTRA_TIME_IN_MILLIS).value(extraTimeInMillis)
        writer.name(TEMPORARILY_BLOCKED).value(temporarilyBlocked)
        writer.name(TEMPORARILY_BLOCKED_END_TIME).value(temporarilyBlockedEndTime)
        writer.name(PARENT_CATEGORY_ID).value(parentCategoryId)
        writer.name(BlOCK_ALL_NOTIFICATIONS).value(blockAllNotifications)
        writer.name(TIME_WARNINGS).value(timeWarnings)
        writer.name(MIN_BATTERY_CHARGING).value(minBatteryLevelWhileCharging)
        writer.name(MIN_BATTERY_MOBILE).value(minBatteryLevelMobile)
        writer.name(SORT).value(sort)
        writer.name(EXTRA_TIME_DAY).value(extraTimeDay)
        writer.name(DISABLE_LIMIITS_UNTIL).value(disableLimitsUntil)
        writer.name(FLAGS).value(flags)
        writer.name(BLOCK_NOTIFICATION_DELAY).value(blockNotificationDelay)

        writer.endObject()
    }

    fun getExtraTime(dayOfEpoch: Int): Long = if (extraTimeDay == -1 || extraTimeDay == dayOfEpoch) {
        extraTimeInMillis
    } else {
        0
    }
}

object CategoryTimeWarnings {
    val durationInMinutesToBitIndex = mapOf(
        1 to 0,
        3 to 1,
        5 to 2,
        10 to 3,
        15 to 4
    )
}

object CategoryFlags {
    const val HAS_BLOCKED_NETWROK_LIST = 1L
    const val ALL = HAS_BLOCKED_NETWROK_LIST
}

fun ImmutableBitmask.withConfigCopiedToOtherDates(sourceDay: Int, targetDays: Set<Int>): ImmutableBitmask {
    val result = dataNotToModify.clone() as BitSet

    val configForSelectedDay = result.get(
            sourceDay * Category.MINUTES_PER_DAY,
            (sourceDay + 1) * Category.MINUTES_PER_DAY
    )

    // update all days
    targetDays.forEach { day ->
        val startWriteIndex = day * Category.MINUTES_PER_DAY

        for (i in 0..(Category.MINUTES_PER_DAY - 1)) {
            result[startWriteIndex + i] = configForSelectedDay[i]
        }
    }

    return ImmutableBitmask(result)
}