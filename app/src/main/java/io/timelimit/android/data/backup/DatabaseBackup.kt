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
package io.timelimit.android.data.backup

import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.data.RoomDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.buffer
import okio.sink
import okio.source
import java.util.concurrent.Executors

class DatabaseBackup(private val context: Context) {
    companion object {
        private const val CONFIG_FILE = "config.json"
        private const val LOG_TAG = "DatabaseBackup"

        private var instance: DatabaseBackup? = null
        private val lock = Object()

        fun with(context: Context): DatabaseBackup {
            if (instance == null) {
                synchronized(lock) {
                    if (instance == null) {
                        instance = DatabaseBackup(context.applicationContext)
                    }
                }
            }

            return instance!!
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val jsonFile = AtomicFile(context.getDatabasePath(CONFIG_FILE))
    private val databaseFile = context.getDatabasePath(RoomDatabase.DEFAULT_DB_NAME)
    private val databaseBackupFile = context.getDatabasePath(RoomDatabase.BACKUP_DB_NAME)
    private val lock = Mutex()

    suspend fun tryRestoreDatabaseBackupAsyncAndWait() {
        executor.executeAndWait { tryRestoreDatabaseBackupSync() }
    }

    private fun tryRestoreDatabaseBackupSync() {
        runBlocking {
            lock.withLock {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "try restoring backup")
                }

                val database = RoomDatabase.with(context)

                if (
                        database.config().getOwnDeviceIdSync().orEmpty().isNotEmpty() ||
                        database.config().getParentModeKeySync() != null
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "database is not empty -> don't restore backup")
                    }

                    return@runBlocking
                }

                try {
                    jsonFile.openRead().use { inputStream ->
                        Threads.database.executeAndWait {
                            DatabaseBackupLowlevel.restoreFromBackupJson(database, inputStream)
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "database was restored from backup")
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "error during restoring database backup", ex)
                    }
                }
            }
        }
    }

    fun tryCreateDatabaseBackupAsync() {
        executor.submit { tryCreateDatabaseBackupSync() }
    }

    private fun tryCreateDatabaseBackupSync() {
        runBlocking {
            lock.withLock {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "create backup")
                }

                try {
                    // create a temp copy of the database
                    databaseBackupFile.delete()
                    databaseFile.source().buffer().readAll(databaseBackupFile.sink(append = false))

                    // open the temp copy
                    val database = RoomDatabase.createOrOpenLocalStorageInstance(context, RoomDatabase.BACKUP_DB_NAME)

                    try {
                        // open the output file
                        val output = jsonFile.startWrite()

                        try {
                            DatabaseBackupLowlevel.outputAsBackupJson(database, output)

                            jsonFile.finishWrite(output)
                        } catch (ex: Exception) {
                            jsonFile.failWrite(output)

                            throw ex
                        }

                        null
                    } finally {
                        database.close()
                    }
                } catch (ex: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.w(LOG_TAG, "failed to create backup", ex)
                    }

                    null
                } finally {
                    // delete the temp copy
                    databaseBackupFile.delete()
                }
            }
        }
    }
}
