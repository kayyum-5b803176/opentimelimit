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

package io.timelimit.android.data.model.derived

import io.timelimit.android.data.Database
import io.timelimit.android.data.invalidation.Observer
import io.timelimit.android.data.invalidation.Table
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserLimitLoginCategoryWithChildId
import java.lang.ref.WeakReference

data class UserLoginRelatedData(
        val user: User,
        val limitLoginCategory: UserLimitLoginCategoryWithChildId?
): Observer {
    companion object {
        private val relatedTables = arrayOf(Table.User, Table.UserLimitLoginCategory, Table.Category)

        fun load(userId: String, database: Database): UserLoginRelatedData? = database.runInUnobservedTransaction {
            val user = database.user().getUserByIdSync(userId) ?: return@runInUnobservedTransaction null
            val limitLoginCategory = database.userLimitLoginCategoryDao().getByParentUserIdSync(userId)

            UserLoginRelatedData(
                    user = user,
                    limitLoginCategory = limitLoginCategory
            ).also {
                database.registerWeakObserver(relatedTables, WeakReference(it))
            }
        }
    }

    private var userInvalidated = false
    private var limitLoginCategoryInvalidated = false

    override fun onInvalidated(tables: Set<Table>) {
        tables.forEach { table ->
            when (table) {
                Table.User -> userInvalidated = true
                Table.UserLimitLoginCategory -> limitLoginCategoryInvalidated = true
                Table.Category -> limitLoginCategoryInvalidated = true
                else -> {/* ignore */}
            }
        }
    }

    fun update(database: Database): UserLoginRelatedData? {
        if (!userInvalidated && !limitLoginCategoryInvalidated) {
            return this
        }

        return database.runInUnobservedTransaction {
            val userId = user.id
            val user = if (userInvalidated) database.user().getUserByIdSync(userId) ?: return@runInUnobservedTransaction null else user
            val limitLoginCategory = if (limitLoginCategoryInvalidated) database.userLimitLoginCategoryDao().getByParentUserIdSync(userId) else limitLoginCategory

            UserLoginRelatedData(
                    user = user,
                    limitLoginCategory = limitLoginCategory
            ).also {
                database.registerWeakObserver(relatedTables, WeakReference(it))
            }
        }
    }
}