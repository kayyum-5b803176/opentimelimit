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
package io.timelimit.android.sync.actions.dispatch

import io.timelimit.android.data.Database
import io.timelimit.android.data.model.UserType
import io.timelimit.android.sync.actions.*

object LocalDatabaseChildActionDispatcher {
    fun dispatchChildActionSync(action: ChildAction, childId: String, database: Database) {
        DatabaseValidation.assertChildExists(database, childId)

        database.runInTransaction {
            when (action) {
                is ChildSignInAction -> {
                    val deviceId = database.config().getOwnDeviceIdSync()!!

                    LocalDatabaseParentActionDispatcher.dispatchParentActionSync(
                            action = SetDeviceUserAction(
                                    deviceId = deviceId,
                                    userId = childId
                            ),
                            database = database,
                            fromChildSelfLimitAddChildUserId = null,
                            parentUserId = null
                    )

                    null
                }
                is ChildChangePasswordAction -> {
                    val userEntry = database.user().getUserByIdSync(childId)

                    if (userEntry == null || userEntry.type != UserType.Child) {
                        throw IllegalArgumentException("invalid user entry")
                    }

                    database.user().updateUserSync(
                            userEntry.copy(
                                    password = action.newPasswordHash
                            )
                    )
                }
            }.let { /* require handling all paths */ }
        }
    }
}