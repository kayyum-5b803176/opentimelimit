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

package io.timelimit.android.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.timelimit.android.data.model.ChildTask
import io.timelimit.android.data.model.derived.ChildTaskWithCategoryTitle
import io.timelimit.android.data.model.derived.FullChildTask

@Dao
interface ChildTaskDao {
    @Query("SELECT * FROM child_task LIMIT :pageSize OFFSET :offset")
    fun getPageSync(offset: Int, pageSize: Int): List<ChildTask>

    @Insert
    fun insertItemsSync(items: List<ChildTask>)

    @Insert
    fun insertItemSync(item: ChildTask)

    @Update
    fun updateItemSync(item: ChildTask)

    @Query("SELECT child_task.*, category.title as category_title FROM child_task JOIN category ON (child_task.category_id = category.id) WHERE category.child_id = :userId")
    fun getTasksByUserIdWithCategoryTitlesLive(userId: String): LiveData<List<ChildTaskWithCategoryTitle>>

    @Query("SELECT child_task.* FROM child_task JOIN category ON (child_task.category_id = category.id) WHERE category.child_id = :userId")
    fun getTasksByUserIdSync(userId: String): List<ChildTask>

    @Query("SELECT child_task.*, category.title as category_title, user.name as child_name, user.timezone AS child_timezone FROM child_task JOIN category ON (child_task.category_id = category.id) JOIN user ON (category.child_id = user.id) WHERE child_task.pending_request = 1")
    fun getPendingTasks(): LiveData<List<FullChildTask>>

    @Query("SELECT * FROM child_task WHERE category_id = :categoryId")
    fun getTasksByCategoryId(categoryId: String): LiveData<List<ChildTask>>

    @Query("SELECT * FROM child_task WHERE task_id = :taskId")
    fun getTaskByTaskId(taskId: String): ChildTask?

    @Query("SELECT * FROM child_task WHERE task_id = :taskId")
    fun getTaskByTaskIdLive(taskId: String): LiveData<ChildTask?>

    @Query("SELECT * FROM child_task WHERE task_id = :taskId")
    suspend fun getTaskByTaskIdCoroutine(taskId: String): ChildTask?

    @Query("DELETE FROM child_task WHERE task_id = :taskId")
    fun removeTaskById(taskId: String)
}