/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.sync.actions.apply

import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.Database
import io.timelimit.android.integration.platform.PlatformIntegration
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.ManipulationLogic
import io.timelimit.android.sync.actions.AppLogicAction
import io.timelimit.android.sync.actions.ChildAction
import io.timelimit.android.sync.actions.ParentAction
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseAppLogicActionDispatcher
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseChildActionDispatcher
import io.timelimit.android.sync.actions.dispatch.LocalDatabaseParentActionDispatcher

object ApplyActionUtil {
    suspend fun applyAppLogicAction(
            action: AppLogicAction,
            appLogic: AppLogic,
            ignoreIfDeviceIsNotConfigured: Boolean
    ) {
        applyAppLogicAction(action, appLogic.database, appLogic.manipulationLogic, ignoreIfDeviceIsNotConfigured)
    }

    private suspend fun applyAppLogicAction(
            action: AppLogicAction,
            database: Database,
            manipulationLogic: ManipulationLogic,
            ignoreIfDeviceIsNotConfigured: Boolean
    ) {
        // uncomment this if you need to know what's dispatching an action
        /*
        if (BuildConfig.DEBUG) {
            try {
                throw Exception()
            } catch (ex: Exception) {
                Log.d(LOG_TAG, "handling action: $action", ex)
            }
        }
        */

        Threads.database.executeAndWait {
            database.runInTransaction {
                val ownDeviceId = database.config().getOwnDeviceIdSync()

                if (ownDeviceId == null && ignoreIfDeviceIsNotConfigured) {
                    return@runInTransaction
                }

                LocalDatabaseAppLogicActionDispatcher.dispatchAppLogicActionSync(action, ownDeviceId!!, database, manipulationLogic)
            }
        }
    }

    suspend fun applyParentAction(action: ParentAction, database: Database, platformIntegration: PlatformIntegration, fromChildSelfLimitAddChildUserId: String?, parentUserId: String?) {
        Threads.database.executeAndWait {
            database.runInTransaction {
                LocalDatabaseParentActionDispatcher.dispatchParentActionSync(action, database, fromChildSelfLimitAddChildUserId, parentUserId)
            }
        }
    }

    suspend fun applyChildAction(action: ChildAction, childUserId: String, database: Database) {
        Threads.database.executeAndWait {
            database.runInTransaction {
                LocalDatabaseChildActionDispatcher.dispatchChildActionSync(action, childUserId, database)
            }
        }
    }
}
