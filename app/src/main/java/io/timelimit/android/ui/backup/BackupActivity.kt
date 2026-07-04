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
package io.timelimit.android.ui.backup

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.RoomDatabase
import io.timelimit.android.data.backup.DatabaseBackupLowlevel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal, self-contained settings screen for manually exporting/importing the
 * full local database as a JSON file, using the Storage Access Framework (SAF)
 * so no storage permissions are required - the user picks the file location
 * themselves via the system file/document picker.
 *
 * Built with plain views instead of the app's usual DataBinding + nav-graph setup,
 * the same way LowBatteryBlockerSettingsActivity is, so it can be dropped in
 * without touching the existing navigation XML. Wire it up from anywhere with:
 *
 *   startActivity(Intent(context, BackupActivity::class.java))
 */
class BackupActivity: AppCompatActivity() {
    private lateinit var statusText: TextView

    private val exportBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportTo(uri)
    }

    private val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmImportFrom(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        val exportButton = Button(this).apply {
            text = "Export backup"
            setOnClickListener {
                val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
                exportBackupLauncher.launch("timelimit-backup-$timestamp.json")
            }
        }
        root.addView(exportButton)

        val importButton = Button(this).apply {
            text = "Import backup"
            setOnClickListener {
                importBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*", "*/*"))
            }
        }
        root.addView(importButton)

        statusText = TextView(this)
        root.addView(statusText)

        setContentView(root)
    }

    private fun setStatus(text: String) {
        statusText.text = text
    }

    private fun exportTo(uri: Uri) {
        setStatus("Exporting backup...")

        runAsync {
            try {
                val database = RoomDatabase.with(this@BackupActivity)

                Threads.database.executeAndWait {
                    val output = contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException("could not open the selected file for writing")

                    output.use { DatabaseBackupLowlevel.outputAsBackupJson(database, it) }
                }

                setStatus("Backup exported successfully.")
                Toast.makeText(this@BackupActivity, "Backup exported", Toast.LENGTH_SHORT).show()
            } catch (ex: Exception) {
                setStatus("Export failed: ${ex.message}")
                Toast.makeText(this@BackupActivity, "Export failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmImportFrom(uri: Uri) {
        AlertDialog.Builder(this)
                .setTitle("Import backup")
                .setMessage(
                        "This replaces ALL data currently stored on this device (devices, " +
                        "categories, rules, used times, ...) with the content of the selected " +
                        "file. This can not be undone. Continue?"
                )
                .setPositiveButton("Import") { _, _ -> importFrom(uri) }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun importFrom(uri: Uri) {
        setStatus("Importing backup...")

        runAsync {
            try {
                val database = RoomDatabase.with(this@BackupActivity)

                Threads.database.executeAndWait {
                    val input = contentResolver.openInputStream(uri)
                            ?: throw IOException("could not open the selected file for reading")

                    input.use { DatabaseBackupLowlevel.restoreFromBackupJson(database, it) }
                }

                setStatus("Backup imported successfully. Please restart the app now.")
                Toast.makeText(this@BackupActivity, "Backup imported - please restart the app", Toast.LENGTH_LONG).show()
            } catch (ex: Exception) {
                setStatus("Import failed: ${ex.message}")
                Toast.makeText(this@BackupActivity, "Import failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
