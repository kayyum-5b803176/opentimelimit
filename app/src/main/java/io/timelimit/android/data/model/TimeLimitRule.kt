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
package io.timelimit.android.data.model

import android.os.Parcelable
import android.util.JsonReader
import android.util.JsonWriter
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.data.customtypes.ImmutableBitmaskAdapter
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.livedata.ignoreUnchanged
import kotlinx.parcelize.Parcelize
import kotlin.experimental.and

@Entity(tableName = "time_limit_rule")
@TypeConverters(ImmutableBitmaskAdapter::class)
@Parcelize
data class TimeLimitRule(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "category_id")
        val categoryId: String,
        @ColumnInfo(name = "apply_to_extra_time_usage")
        val applyToExtraTimeUsage: Boolean,
        @ColumnInfo(name = "day_mask")
        val dayMask: Byte,
        @ColumnInfo(name = "max_time")
        val maximumTimeInMillis: Int,
        @ColumnInfo(name = "start_minute_of_day")
        val startMinuteOfDay: Int,
        @ColumnInfo(name = "end_minute_of_day")
        val endMinuteOfDay: Int,
        @ColumnInfo(name = "session_duration_milliseconds")
        val sessionDurationMilliseconds: Int,
        @ColumnInfo(name = "session_pause_milliseconds")
        val sessionPauseMilliseconds: Int,
        @ColumnInfo(name = "per_day")
        val perDay: Boolean,
        @ColumnInfo(name = "expires_at")
        val expiresAt: Long?
): Parcelable, JsonSerializable {
    companion object {
        private const val RULE_ID = "ruleId"
        private const val CATEGORY_ID = "categoryId"
        private const val MAX_TIME_IN_MILLIS = "time"
        private const val DAY_MASK = "days"
        private const val APPLY_TO_EXTRA_TIME_USAGE = "extraTime"
        private const val START_MINUTE_OF_DAY = "start"
        private const val END_MINUTE_OF_DAY = "end"
        private const val SESSION_DURATION_MILLISECONDS = "dur"
        private const val SESSION_PAUSE_MILLISECONDS = "pause"
        private const val PER_DAY = "perDay"
        private const val EXPIRES_AT = "e"

        const val MIN_START_MINUTE = MinuteOfDay.MIN
        const val MAX_END_MINUTE = MinuteOfDay.MAX

        fun parse(reader: JsonReader): TimeLimitRule {
            var id: String? = null
            var categoryId: String? = null
            var applyToExtraTimeUsage: Boolean? = null
            var dayMask: Byte? = null
            var maximumTimeInMillis: Int? = null
            var startMinuteOfDay = MIN_START_MINUTE
            var endMinuteOfDay = MAX_END_MINUTE
            var sessionDurationMilliseconds = 0
            var sessionPauseMilliseconds = 0
            var perDay = false
            var expiresAt: Long? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    RULE_ID -> id = reader.nextString()
                    CATEGORY_ID -> categoryId = reader.nextString()
                    MAX_TIME_IN_MILLIS -> maximumTimeInMillis = reader.nextInt()
                    DAY_MASK -> dayMask = reader.nextInt().toByte()
                    APPLY_TO_EXTRA_TIME_USAGE -> applyToExtraTimeUsage = reader.nextBoolean()
                    START_MINUTE_OF_DAY -> startMinuteOfDay = reader.nextInt()
                    END_MINUTE_OF_DAY -> endMinuteOfDay = reader.nextInt()
                    SESSION_DURATION_MILLISECONDS -> sessionDurationMilliseconds = reader.nextInt()
                    SESSION_PAUSE_MILLISECONDS -> sessionPauseMilliseconds = reader.nextInt()
                    PER_DAY -> perDay = reader.nextBoolean()
                    EXPIRES_AT -> expiresAt = reader.nextLong()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return TimeLimitRule(
                    id = id!!,
                    categoryId = categoryId!!,
                    applyToExtraTimeUsage = applyToExtraTimeUsage!!,
                    dayMask = dayMask!!,
                    maximumTimeInMillis = maximumTimeInMillis!!,
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    sessionDurationMilliseconds = sessionDurationMilliseconds,
                    sessionPauseMilliseconds = sessionPauseMilliseconds,
                    perDay = perDay,
                    expiresAt = expiresAt
            )
        }
    }

    init {
        IdGenerator.assertIdValid(id)
        IdGenerator.assertIdValid(categoryId)

        if (maximumTimeInMillis < 0) {
            throw IllegalArgumentException("maximumTimeInMillis $maximumTimeInMillis < 0")
        }

        if (dayMask < 0 || dayMask > (1 or 2 or 4 or 8 or 16 or 32 or 64)) {
            throw IllegalArgumentException()
        }

        if (startMinuteOfDay < MIN_START_MINUTE || endMinuteOfDay > MAX_END_MINUTE || startMinuteOfDay > endMinuteOfDay) {
            throw IllegalArgumentException()
        }

        if (sessionDurationMilliseconds < 0 || sessionPauseMilliseconds < 0) {
            throw IllegalArgumentException()
        }

        if (expiresAt != null && expiresAt <= 0) {
            throw IllegalArgumentException()
        }
    }

    val appliesToWholeDay: Boolean
        get() = startMinuteOfDay == MIN_START_MINUTE && endMinuteOfDay == MAX_END_MINUTE

    val sessionDurationLimitEnabled: Boolean
        get() = sessionPauseMilliseconds > 0 && sessionDurationMilliseconds > 0

    val appliesToMultipleDays: Boolean
        get() = dayMask.toInt().countOneBits() > 1

    val likeBlockedTimeArea: Boolean
        get() = applyToExtraTimeUsage && maximumTimeInMillis == 0

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(RULE_ID).value(id)
        writer.name(CATEGORY_ID).value(categoryId)
        writer.name(MAX_TIME_IN_MILLIS).value(maximumTimeInMillis)
        writer.name(DAY_MASK).value(dayMask)
        writer.name(APPLY_TO_EXTRA_TIME_USAGE).value(applyToExtraTimeUsage)
        writer.name(START_MINUTE_OF_DAY).value(startMinuteOfDay)
        writer.name(END_MINUTE_OF_DAY).value(endMinuteOfDay)

        if (sessionDurationMilliseconds != 0 || sessionPauseMilliseconds != 0) {
            writer.name(SESSION_DURATION_MILLISECONDS).value(sessionDurationMilliseconds)
            writer.name(SESSION_PAUSE_MILLISECONDS).value(sessionPauseMilliseconds)
        }

        writer.name(PER_DAY).value(perDay)
        if (expiresAt != null) writer.name(EXPIRES_AT).value(expiresAt)

        writer.endObject()
    }

    fun isAtLeastAsStrictAs(other: TimeLimitRule): Boolean =
        this.categoryId == other.categoryId &&
                this.maximumTimeInMillis <= other.maximumTimeInMillis &&
                this.dayMask and other.dayMask == other.dayMask &&
                (this.applyToExtraTimeUsage || !other.applyToExtraTimeUsage) &&
                startMinuteOfDay <= other.startMinuteOfDay &&
                endMinuteOfDay >= other.endMinuteOfDay &&
                (!other.sessionDurationLimitEnabled || (
                        this.sessionDurationMilliseconds <= other.sessionDurationMilliseconds &&
                                this.sessionPauseMilliseconds >= other.sessionPauseMilliseconds
                        )) &&
                (!this.perDay || other.perDay || other.dayMask.countOneBits() <= 1) &&
                (this.expiresAt == null || (other.expiresAt != null && this.expiresAt >= other.expiresAt))
}

fun List<TimeLimitRule>.getSlotSwitchMinutes(): Set<Int> {
    val result = mutableSetOf<Int>()

    result.add(MinuteOfDay.MIN)

    forEach { rule -> result.add(rule.startMinuteOfDay); result.add(rule.endMinuteOfDay) }

    return result
}

fun getCurrentTimeSlotStartMinute(slots: Set<Int>, minuteOfDay: LiveData<Int>): LiveData<Int> = minuteOfDay.map { minuteOfDay ->
    slots.find { it >= minuteOfDay } ?: 0
}.ignoreUnchanged()