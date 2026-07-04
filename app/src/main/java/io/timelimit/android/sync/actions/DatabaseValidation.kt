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
package io.timelimit.android.sync.actions

import io.timelimit.android.data.Database
import io.timelimit.android.data.model.UserType

object DatabaseValidation {
    fun assertCategoryExists(database: Database, categoryId: String) {
        database.category().getCategoryByIdSync(categoryId)
                ?: throw IllegalArgumentException("category with the specified id does not exist")
    }

    fun assertChildExists(database: Database, childId: String) {
        val userEntry = database.user().getUserByIdSync(childId)

        if (userEntry == null || userEntry.type != UserType.Child) {
            throw IllegalArgumentException("child with the specified id does not exist")
        }
    }

    fun assertUserExists(database: Database, userId: String) {
        database.user().getUserByIdSync(userId)
                ?:throw IllegalArgumentException("user with the specified id does not exist")
    }

    fun assertTimelimitRuleExists(database: Database, timeLimitRuleId: String) {
        database.timeLimitRules().getTimeLimitRuleByIdSync(timeLimitRuleId)
                ?:throw IllegalArgumentException("time limit rule with the specified id does not exist")
    }

    fun assertDeviceExists(database: Database, devcieId: String) {
        database.device().getDeviceByIdSync(devcieId)
                ?:throw IllegalArgumentException("device does not exist")
    }
}
