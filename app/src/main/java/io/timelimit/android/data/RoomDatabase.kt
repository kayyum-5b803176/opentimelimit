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

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.timelimit.android.async.Threads
import io.timelimit.android.data.dao.DerivedDataDao
import io.timelimit.android.data.invalidation.Observer
import io.timelimit.android.data.invalidation.Table
import io.timelimit.android.data.invalidation.TableUtil
import io.timelimit.android.data.model.*
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Database(entities = [
    User::class,
    Device::class,
    App::class,
    CategoryApp::class,
    Category::class,
    UsedTimeItem::class,
    TimeLimitRule::class,
    ConfigurationItem::class,
    TemporarilyAllowedApp::class,
    AppActivity::class,
    AllowedContact::class,
    UserKey::class,
    SessionDuration::class,
    UserLimitLoginCategory::class,
    CategoryNetworkId::class,
    ChildTask::class,
    CategoryTimeWarning::class,
    UserU2FKey::class,
    WidgetCategory::class,
    WidgetConfig::class
], version = 31, autoMigrations = [
    AutoMigration(from = 29, to = 30),
    AutoMigration(from = 30, to = 31)
])
abstract class RoomDatabase: RoomDatabase(), io.timelimit.android.data.Database {
    companion object {
        private val lock = Object()
        private var instance: io.timelimit.android.data.Database? = null
        const val DEFAULT_DB_NAME = "db"
        const val BACKUP_DB_NAME = "db2"

        fun with(context: Context): io.timelimit.android.data.Database {
            if (instance == null) {
                synchronized(lock) {
                    if (instance == null) {
                        instance = createOrOpenLocalStorageInstance(context, DEFAULT_DB_NAME)
                    }
                }
            }

            return instance!!
        }

        fun createInMemoryInstance(context: Context): io.timelimit.android.data.Database {
            return Room.inMemoryDatabaseBuilder(
                    context,
                    io.timelimit.android.data.RoomDatabase::class.java
            ).build()
        }

        fun createOrOpenLocalStorageInstance(context: Context, name: String): io.timelimit.android.data.Database {
            return Room.databaseBuilder(
                context,
                io.timelimit.android.data.RoomDatabase::class.java,
                name
            )
                .setJournalMode(JournalMode.TRUNCATE)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addMigrations(
                    DatabaseMigrations.MIGRATE_TO_V2,
                    DatabaseMigrations.MIGRATE_TO_V3,
                    DatabaseMigrations.MIGRATE_TO_V4,
                    DatabaseMigrations.MIGRATE_TO_V5,
                    DatabaseMigrations.MIGRATE_TO_V6,
                    DatabaseMigrations.MIGRATE_TO_V7,
                    DatabaseMigrations.MIGRATE_TO_V8,
                    DatabaseMigrations.MIGRATE_TO_V9,
                    DatabaseMigrations.MIGRATE_TO_V10,
                    DatabaseMigrations.MIGRATE_TO_V11,
                    DatabaseMigrations.MIGRATE_TO_V12,
                    DatabaseMigrations.MIGRATE_TO_V13,
                    DatabaseMigrations.MIGRATE_TO_V14,
                    DatabaseMigrations.MIGRATE_TO_V15,
                    DatabaseMigrations.MIGRATE_TO_V16,
                    DatabaseMigrations.MIGRATE_TO_V17,
                    DatabaseMigrations.MIGRATE_TO_V18,
                    DatabaseMigrations.MIGRATE_TO_V19,
                    DatabaseMigrations.MIGRATE_TO_V20,
                    DatabaseMigrations.MIGRATE_TO_V21,
                    DatabaseMigrations.MIGRATE_TO_V22,
                    DatabaseMigrations.MIGRATE_TO_V23,
                    DatabaseMigrations.MIGRATE_TO_V24,
                    DatabaseMigrations.MIGRATE_TO_V25,
                    DatabaseMigrations.MIGRATE_TO_V26,
                    DatabaseMigrations.MIGRATE_TO_V27,
                    DatabaseMigrations.MIGRATE_TO_V28,
                    DatabaseMigrations.MIGRATE_TO_V29
                )
                .setQueryExecutor(Threads.database)
                .addCallback(object: Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)

                        db.query("PRAGMA journal_mode = PERSIST").consume()
                        db.query("PRAGMA journal_size_limit = 32768").consume()
                    }
                })
                .build()
        }
    }

    private val derivedDataDao: DerivedDataDao by lazy { DerivedDataDao(this) }
    override fun derivedDataDao(): DerivedDataDao = derivedDataDao

    private val transactionCommitListeners = mutableSetOf<() -> Unit>()

    override fun registerTransactionCommitListener(listener: () -> Unit): Unit = synchronized(transactionCommitListeners) {
        transactionCommitListeners.add(listener)
    }

    override fun unregisterTransactionCommitListener(listener: () -> Unit): Unit = synchronized(transactionCommitListeners) {
        transactionCommitListeners.remove(listener)
    }

    override fun <T> runInUnobservedTransaction(block: () -> T): T {
        openHelper.readableDatabase.beginTransaction()
        try {
            val result = block()

            openHelper.readableDatabase.setTransactionSuccessful()

            return result
        } finally {
            openHelper.readableDatabase.endTransaction()
        }
    }

    @Deprecated(
        "endTransaction() is deprecated",
        replaceWith = ReplaceWith("runInTransaction(Runnable)")
    )
    @SuppressLint("RestrictedApi")
    override fun endTransaction() {
        openHelper.writableDatabase.endTransaction()

        if (!inTransaction()) {
            // block the query thread of room until this is done
            val latch = CountDownLatch(1)

            try {
                queryExecutor.execute { latch.await(5, TimeUnit.SECONDS) }

                // without requesting a async refresh, no sync refresh will happen
                invalidationTracker.refreshVersionsAsync()
                invalidationTracker.refreshVersionsSync()
            } finally {
                latch.countDown()
            }

            openHelper.readableDatabase.beginTransaction()
            try {
                synchronized(transactionCommitListeners) { transactionCommitListeners.toList() }.forEach { it() }
            } finally {
                openHelper.readableDatabase.endTransaction()
            }
        }
    }

    override fun registerWeakObserver(tables: Array<Table>, observer: WeakReference<Observer>) {
        val tableNames = arrayOfNulls<String>(tables.size)

        tables.forEachIndexed { index, table ->
            tableNames[index] = TableUtil.toName(table)
        }

        invalidationTracker.addObserver(object: InvalidationTracker.Observer(tableNames.requireNoNulls()) {
            override fun onInvalidated(tables: Set<String>) {
                val item = observer.get()

                if (item != null) {
                    item.onInvalidated(tables.map { TableUtil.toEnum(it) }.toSet())
                } else {
                    invalidationTracker.removeObserver(this)
                }
            }
        })
    }

    override fun deleteAllData() {
        clearAllTables()
    }

    override fun close() {
        super.close()
    }
}
