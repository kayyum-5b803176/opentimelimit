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

package io.timelimit.android.data.invalidation

enum class Table {
    AllowedContact,
    App,
    AppActivity,
    Category,
    CategoryApp,
    ConfigurationItem,
    Device,
    SessionDuration,
    TemporarilyAllowedApp,
    TimeLimitRule,
    UsedTimeItem,
    User,
    UserKey,
    UserLimitLoginCategory,
    CategoryNetworkId,
    CategoryTimeWarning
}

object TableNames {
    const val ALLOWED_CONTACT = "allowed_contact"
    const val APP = "app"
    const val APP_ACTIVITY = "app_activity"
    const val CATEGORY = "category"
    const val CATEGORY_APP = "category_app"
    const val CONFIGURATION_ITEM = "config"
    const val DEVICE = "device"
    const val SESSION_DURATION = "session_duration"
    const val TEMPORARILY_ALLOWED_APP = "temporarily_allowed_app"
    const val TIME_LIMIT_RULE = "time_limit_rule"
    const val USED_TIME_ITEM = "used_time"
    const val USER = "user"
    const val USER_KEY = "user_key"
    const val USER_LIMIT_LOGIN_CATEGORY = "user_limit_login_category"
    const val CATEGORY_NETWORK_ID = "category_network_id"
    const val CATEGORY_TIME_WARNING = "category_time_warning"
}

object TableUtil {
    fun toName(value: Table): String = when (value) {
        Table.AllowedContact -> TableNames.ALLOWED_CONTACT
        Table.App -> TableNames.APP
        Table.AppActivity -> TableNames.APP_ACTIVITY
        Table.Category -> TableNames.CATEGORY
        Table.CategoryApp -> TableNames.CATEGORY_APP
        Table.ConfigurationItem -> TableNames.CONFIGURATION_ITEM
        Table.Device -> TableNames.DEVICE
        Table.SessionDuration -> TableNames.SESSION_DURATION
        Table.TemporarilyAllowedApp -> TableNames.TEMPORARILY_ALLOWED_APP
        Table.TimeLimitRule -> TableNames.TIME_LIMIT_RULE
        Table.UsedTimeItem -> TableNames.USED_TIME_ITEM
        Table.User -> TableNames.USER
        Table.UserKey -> TableNames.USER_KEY
        Table.UserLimitLoginCategory -> TableNames.USER_LIMIT_LOGIN_CATEGORY
        Table.CategoryNetworkId -> TableNames.CATEGORY_NETWORK_ID
        Table.CategoryTimeWarning -> TableNames.CATEGORY_TIME_WARNING
    }

    fun toEnum(value: String): Table = when (value) {
        TableNames.ALLOWED_CONTACT -> Table.AllowedContact
        TableNames.APP -> Table.App
        TableNames.APP_ACTIVITY -> Table.AppActivity
        TableNames.CATEGORY -> Table.Category
        TableNames.CATEGORY_APP -> Table.CategoryApp
        TableNames.CONFIGURATION_ITEM -> Table.ConfigurationItem
        TableNames.DEVICE -> Table.Device
        TableNames.SESSION_DURATION -> Table.SessionDuration
        TableNames.TEMPORARILY_ALLOWED_APP -> Table.TemporarilyAllowedApp
        TableNames.TIME_LIMIT_RULE -> Table.TimeLimitRule
        TableNames.USED_TIME_ITEM -> Table.UsedTimeItem
        TableNames.USER -> Table.User
        TableNames.USER_KEY -> Table.UserKey
        TableNames.USER_LIMIT_LOGIN_CATEGORY -> Table.UserLimitLoginCategory
        TableNames.CATEGORY_NETWORK_ID -> Table.CategoryNetworkId
        TableNames.CATEGORY_TIME_WARNING -> Table.CategoryTimeWarning
        else -> throw IllegalArgumentException()
    }
}