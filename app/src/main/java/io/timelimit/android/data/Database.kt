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
package io.timelimit.android.data

import io.timelimit.android.data.dao.*
import io.timelimit.android.data.invalidation.Observer
import io.timelimit.android.data.invalidation.Table
import java.lang.ref.WeakReference
import java.util.concurrent.Callable

interface Database {
    fun app(): AppDao
    fun categoryApp(): CategoryAppDao
    fun category(): CategoryDao
    fun config(): ConfigDao
    fun device(): DeviceDao
    fun timeLimitRules(): TimeLimitRuleDao
    fun usedTimes(): UsedTimeDao
    fun user(): UserDao
    fun temporarilyAllowedApp(): TemporarilyAllowedAppDao
    fun appActivity(): AppActivityDao
    fun allowedContact(): AllowedContactDao
    fun userKey(): UserKeyDao
    fun sessionDuration(): SessionDurationDao
    fun derivedDataDao(): DerivedDataDao
    fun userLimitLoginCategoryDao(): UserLimitLoginCategoryDao
    fun categoryNetworkId(): CategoryNetworkIdDao
    fun childTasks(): ChildTaskDao
    fun timeWarning(): CategoryTimeWarningDao
    fun u2f(): U2FDao
    fun widgetCategory(): WidgetCategoryDao
    fun widgetConfig(): WidgetConfigDao

    fun <T> runInTransaction(body: Callable<T>): T
    fun <T> runInUnobservedTransaction(block: () -> T): T
    fun registerWeakObserver(tables: Array<Table>, observer: WeakReference<Observer>)
    fun registerTransactionCommitListener(listener: () -> Unit)
    fun unregisterTransactionCommitListener(listener: () -> Unit)

    fun deleteAllData()
    fun close()
}