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
import io.timelimit.android.data.Database
import io.timelimit.android.data.cache.multi.DataCacheHelperInterface
import io.timelimit.android.data.cache.multi.createCache
import io.timelimit.android.data.cache.multi.delayClosingItems
import io.timelimit.android.data.cache.multi.openLiveAtDatabaseThread
import io.timelimit.android.data.cache.single.SingleItemDataCacheHelperInterface
import io.timelimit.android.data.cache.single.createCache
import io.timelimit.android.data.cache.single.delayClosingItem
import io.timelimit.android.data.cache.single.openLiveAtDatabaseThread
import io.timelimit.android.data.model.derived.*

class DerivedDataDao (private val database: Database) {
    private val userRelatedDataCache = object : DataCacheHelperInterface<String, UserRelatedData?, UserRelatedData?> {
        override fun openItemSync(key: String): UserRelatedData? {
            val user = database.user().getUserByIdSync(key) ?: return null

            return UserRelatedData.load(user, database)
        }

        override fun updateItemSync(key: String, item: UserRelatedData?): UserRelatedData? = if (item != null) item.update(database) else openItemSync(key)
        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }
        override fun disposeItemFast(key: String, item: UserRelatedData?) = Unit
        override fun prepareForUser(item: UserRelatedData?): UserRelatedData? = item
    }.createCache()

    private val deviceRelatedDataCache = object: SingleItemDataCacheHelperInterface<DeviceRelatedData?, DeviceRelatedData?> {
        override fun openItemSync(): DeviceRelatedData? = DeviceRelatedData.load(database)
        override fun updateItemSync(item: DeviceRelatedData?): DeviceRelatedData? = if (item != null) item.update(database) else openItemSync()
        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }
        override fun prepareForUser(item: DeviceRelatedData?): DeviceRelatedData? = item
        override fun disposeItemFast(item: DeviceRelatedData?): Unit = Unit
    }.createCache()

    private val userLoginRelatedDataCache = object: DataCacheHelperInterface<String, UserLoginRelatedData?, UserLoginRelatedData?> {
        override fun openItemSync(key: String): UserLoginRelatedData? = UserLoginRelatedData.load(key, database)
        override fun updateItemSync(key: String, item: UserLoginRelatedData?): UserLoginRelatedData? = if (item != null) item.update(database) else openItemSync(key)
        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }
        override fun disposeItemFast(key: String, item: UserLoginRelatedData?) = Unit
        override fun prepareForUser(item: UserLoginRelatedData?): UserLoginRelatedData? = item
    }.createCache()

    private val usableUserRelatedData = userRelatedDataCache.userInterface.delayClosingItems(15 * 1000 /* 15 seconds */)
    private val usableDeviceRelatedData = deviceRelatedDataCache.userInterface.delayClosingItem(60 * 1000 /* 1 minute */)
    private val usableUserLoginRelatedDataCache = userLoginRelatedDataCache.userInterface.delayClosingItems(15 * 1000 /* 15 seconds */)

    private val deviceAndUserRelatedDataCache = object: SingleItemDataCacheHelperInterface<DeviceAndUserRelatedData?, DeviceAndUserRelatedData?> {
        override fun openItemSync(): DeviceAndUserRelatedData?  {
            val deviceRelatedData = usableDeviceRelatedData.openSync(null) ?: return null
            val userRelatedData = if (deviceRelatedData.deviceEntry.currentUserId.isNotEmpty())
                usableUserRelatedData.openSync(deviceRelatedData.deviceEntry.currentUserId, null)
            else
                null

            return DeviceAndUserRelatedData(
                    deviceRelatedData = deviceRelatedData,
                    userRelatedData = userRelatedData
            )
        }

        override fun updateItemSync(item: DeviceAndUserRelatedData?): DeviceAndUserRelatedData? {
            val newItem = openItemSync()

            return if (newItem != item) newItem else {
                disposeItemFast(newItem)
                item
            }
        }

        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }

        override fun prepareForUser(item: DeviceAndUserRelatedData?): DeviceAndUserRelatedData? = item

        override fun disposeItemFast(item: DeviceAndUserRelatedData?) {
            usableDeviceRelatedData.close(null)
            item?.deviceRelatedData?.deviceEntry?.currentUserId?.let {
                if (it.isNotEmpty()) {
                    usableUserRelatedData.close(it, null)
                }
            }
        }
    }.createCache()

    private val completeUserLoginRelatedData = object: DataCacheHelperInterface<String, CompleteUserLoginRelatedData?, CompleteUserLoginRelatedData?> {
        override fun openItemSync(key: String): CompleteUserLoginRelatedData? = database.runInUnobservedTransaction {
            val userLoginRelatedData = usableUserLoginRelatedDataCache.openSync(key, null)
            val deviceRelatedData = usableDeviceRelatedData.openSync(null)

            val limitLoginCategoryUserRelatedData = if (userLoginRelatedData?.limitLoginCategory == null)
                null
            else {
                usableUserRelatedData.openSync(userLoginRelatedData.limitLoginCategory.childId, null)
            }

            if (userLoginRelatedData == null || deviceRelatedData == null) {
                null
            } else {
                CompleteUserLoginRelatedData(
                        loginRelatedData = userLoginRelatedData,
                        deviceRelatedData = deviceRelatedData,
                        limitLoginCategoryUserRelatedData = limitLoginCategoryUserRelatedData
                )
            }
        }

        override fun updateItemSync(key: String, item: CompleteUserLoginRelatedData?): CompleteUserLoginRelatedData? {
            val newItem = openItemSync(key)

            return if (newItem != item) newItem else {
                disposeItemFast(key, newItem)
                item
            }
        }

        override fun disposeItemFast(key: String, item: CompleteUserLoginRelatedData?) {
            usableUserLoginRelatedDataCache.close(key, null)
            usableDeviceRelatedData.close(null)
            item?.loginRelatedData?.limitLoginCategory?.let { category ->
                usableUserRelatedData.close(category.childId, null)
            }
        }

        override fun <R> wrapOpenOrUpdate(block: () -> R): R = database.runInUnobservedTransaction { block() }
        override fun prepareForUser(item: CompleteUserLoginRelatedData?): CompleteUserLoginRelatedData? = item
    }.createCache()

    private val usableDeviceAndUserRelatedDataCache = deviceAndUserRelatedDataCache.userInterface.delayClosingItem(5000)
    private val usableCompleteUserLoginRelatedData = completeUserLoginRelatedData.userInterface.delayClosingItems(5000)

    private val deviceAndUserRelatedDataLive = usableDeviceAndUserRelatedDataCache.openLiveAtDatabaseThread()

    init {
        database.registerTransactionCommitListener {
            userRelatedDataCache.ownerInterface.updateSync()
            deviceRelatedDataCache.ownerInterface.updateSync()
            userLoginRelatedDataCache.ownerInterface.updateSync()
            deviceAndUserRelatedDataCache.ownerInterface.updateSync()
            completeUserLoginRelatedData.ownerInterface.updateSync()
        }
    }

    fun getUserAndDeviceRelatedDataSync(): DeviceAndUserRelatedData? {
        val result = usableDeviceAndUserRelatedDataCache.openSync(null)

        usableDeviceAndUserRelatedDataCache.close(null)

        return result
    }

    fun getUserLoginRelatedDataSync(userId: String): CompleteUserLoginRelatedData? {
        val result = usableCompleteUserLoginRelatedData.openSync(userId, null)

        usableCompleteUserLoginRelatedData.close(userId, null)

        return result
    }

    fun getUserRelatedDataSync(userId: String): UserRelatedData? {
        val result = usableUserRelatedData.openSync(userId, null)

        usableUserRelatedData.close(userId, null)

        return result
    }

    fun getUserAndDeviceRelatedDataLive(): LiveData<DeviceAndUserRelatedData?> = deviceAndUserRelatedDataLive

    fun getUserRelatedDataLive(userId: String): LiveData<UserRelatedData?> = usableUserRelatedData.openLiveAtDatabaseThread(userId)

    fun getUserLoginRelatedDataLive(userId: String) = usableCompleteUserLoginRelatedData.openLiveAtDatabaseThread(userId)
}