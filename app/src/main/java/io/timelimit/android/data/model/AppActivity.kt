/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
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
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(primaryKeys = ["device_id", "app_package_name", "activity_class_name"], tableName = "app_activity")
data class AppActivity(
        @ColumnInfo(name = "device_id")
        val deviceId: String,
        @ColumnInfo(name = "app_package_name")
        val appPackageName: String,
        @ColumnInfo(name = "activity_class_name")
        val activityClassName: String,
        @ColumnInfo(name = "activity_title")
        val title: String
): JsonSerializable {
    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val APP_PACKAGE_NAME = "app_package_name"
        private const val ACTIVITY_CLASS_NAME = "activity_class_name"
        private const val TITLE = "title"

        fun parse(reader: JsonReader): AppActivity {
            var deviceId: String? = null
            var appPackageName: String? = null
            var activityClassName: String? = null
            var title: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DEVICE_ID -> deviceId = reader.nextString()
                    APP_PACKAGE_NAME -> appPackageName = reader.nextString()
                    ACTIVITY_CLASS_NAME -> activityClassName = reader.nextString()
                    TITLE -> title = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return AppActivity(
                    deviceId = deviceId!!,
                    appPackageName = appPackageName!!,
                    activityClassName = activityClassName!!,
                    title = title!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(DEVICE_ID).value(deviceId)
        writer.name(APP_PACKAGE_NAME).value(appPackageName)
        writer.name(ACTIVITY_CLASS_NAME).value(activityClassName)
        writer.name(TITLE).value(title)

        writer.endObject()
    }
}
