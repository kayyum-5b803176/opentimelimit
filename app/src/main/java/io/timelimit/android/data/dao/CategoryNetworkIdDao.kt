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
import androidx.room.Query
import io.timelimit.android.data.model.CategoryNetworkId

@Dao
interface CategoryNetworkIdDao {
    @Query("SELECT * FROM category_network_id LIMIT :pageSize OFFSET :offset")
    fun getPageSync(offset: Int, pageSize: Int): List<CategoryNetworkId>

    @Query("SELECT * FROM category_network_id WHERE category_id = :categoryId")
    fun getByCategoryIdLive(categoryId: String): LiveData<List<CategoryNetworkId>>

    @Query("SELECT * FROM category_network_id WHERE category_id = :categoryId")
    fun getByCategoryIdSync(categoryId: String): List<CategoryNetworkId>

    @Query("SELECT * FROM category_network_id WHERE category_id = :categoryId AND network_item_id = :itemId")
    fun getByCategoryIdAndItemIdSync(categoryId: String, itemId: String): CategoryNetworkId?

    @Query("SELECT COUNT(*) FROM category_network_id WHERE category_id = :categoryId")
    fun countByCategoryIdSync(categoryId: String): Long

    @Insert
    fun insertItemsSync(items: List<CategoryNetworkId>)

    @Insert
    fun insertItemSync(item: CategoryNetworkId)

    @Query("DELETE FROM category_network_id WHERE category_id = :categoryId")
    fun deleteByCategoryId(categoryId: String)
}