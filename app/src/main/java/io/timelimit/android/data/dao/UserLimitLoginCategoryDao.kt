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

package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.timelimit.android.data.model.UserLimitLoginCategory
import io.timelimit.android.data.model.UserLimitLoginCategoryWithChildId

@Dao
interface UserLimitLoginCategoryDao {
    @Query("SELECT * FROM user_limit_login_category LIMIT :pageSize OFFSET :offset")
    fun getAllowedContactPageSync(offset: Int, pageSize: Int): List<UserLimitLoginCategory>

    @Insert
    fun addItemsSync(item: List<UserLimitLoginCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplaceItemSync(item: UserLimitLoginCategory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnoreItemSync(item: UserLimitLoginCategory)

    @Query("SELECT child_user.id AS child_id, child_user.name AS child_title, category.id AS category_id, category.title AS category_title, 1 AS selected, user_limit_login_category.pre_block_duration AS pre_block_duration FROM user_limit_login_category JOIN category ON (user_limit_login_category.category_id = category.id) JOIN user child_user ON (category.child_id = child_user.id) WHERE user_limit_login_category.user_id = :parentUserId")
    fun getByParentUserIdLive(parentUserId: String): LiveData<UserLimitLoginCategoryWithChildId?>

    @Query("SELECT child_user.id AS child_id, child_user.name AS child_title, category.id AS category_id, category.title AS category_title, 1 AS selected, user_limit_login_category.pre_block_duration AS pre_block_duration FROM user_limit_login_category JOIN category ON (user_limit_login_category.category_id = category.id) JOIN user child_user ON (category.child_id = child_user.id) WHERE user_limit_login_category.user_id = :parentUserId")
    fun getByParentUserIdSync(parentUserId: String): UserLimitLoginCategoryWithChildId?

    @Query("SELECT child_user.id AS child_id, child_user.name AS child_title, category.id AS category_id, category.title AS category_title, CASE WHEN category.id IN (SELECT user_limit_login_category.category_id FROM user_limit_login_category WHERE user_limit_login_category.user_id = :parentUserId) THEN 1 ELSE 0 END AS selected, 0 as pre_block_duration FROM user child_user JOIN category category ON (category.child_id = child_user.id)")
    fun getLimitLoginCategoryOptions(parentUserId: String): LiveData<List<UserLimitLoginCategoryWithChildId>>

    @Query("SELECT COUNT(*) FROM user WHERE user.id != :userId AND user.type = 'parent' AND user.id NOT IN (SELECT user_id FROM user_limit_login_category)")
    fun countOtherUsersWithoutLimitLoginCategoryLive(userId: String): LiveData<Long>

    @Query("SELECT COUNT(*) FROM user WHERE user.id != :userId AND user.type = 'parent' AND user.id NOT IN (SELECT user_id FROM user_limit_login_category)")
    fun countOtherUsersWithoutLimitLoginCategorySync(userId: String): Long

    @Query("DELETE FROM user_limit_login_category WHERE user_id = :userId")
    fun removeItemSync(userId: String)
}