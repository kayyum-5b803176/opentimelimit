/*
 * TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.diagnose

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.JsonWriter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.DiagnoseForegroundAppFragmentBinding
import io.timelimit.android.integration.platform.android.foregroundapp.InstanceIdForegroundAppHelper
import io.timelimit.android.integration.platform.android.foregroundapp.usagestats.DirectUsageStatsReader
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.livedata.liveDataFromNullableValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.util.TimeTextUtil
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class DiagnoseForegroundAppFragment : Fragment(), FragmentWithCustomTitle {
    companion object {
        private const val LOG_TAG = "DiagnoseForegroundApp"
        private const val REQ_EXPORT_TEXT = 2

        private val buttonIntervals = listOf(
                0,
                5 * 1000,
                30 * 1000,
                60 * 1000,
                15 * 60 * 1000,
                60 * 60 * 1000,
                24 * 60 * 60 * 1000,
                7 * 24 * 60 * 60 * 1000
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val activity: ActivityViewModelHolder = activity as ActivityViewModelHolder
        val binding = DiagnoseForegroundAppFragmentBinding.inflate(inflater, container, false)
        val auth = activity.getActivityViewModel()
        val logic = DefaultAppLogic.with(requireContext())
        val currentValue = logic.database.config().getForegroundAppQueryIntervalAsync()
        val currentId = currentValue.map {
            val res = buttonIntervals.indexOf(it.toInt())

            if (res == -1)
                0
            else
                res
        }

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                doesSupportAuth = liveDataFromNonNullValue(true),
                fragment = this
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        val allButtons = buttonIntervals.mapIndexed { index, interval ->
            RadioButton(requireContext()).apply {
                id = index

                if (interval == 0) {
                    setText(R.string.diagnose_fga_query_range_min)
                } else if (interval < 60 * 1000) {
                    text = TimeTextUtil.seconds(interval / 1000, requireContext())
                } else {
                    text = TimeTextUtil.time(interval, requireContext())
                }
            }
        }

        allButtons.forEach { binding.radioGroup.addView(it) }

        currentId.observe(viewLifecycleOwner) { binding.radioGroup.check(it) }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val oldId = currentId.value

            if (oldId != null && checkedId != oldId) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    val newValue = buttonIntervals[checkedId]

                    Threads.database.execute {
                        logic.database.config().setForegroundAppQueryIntervalSync(newValue.toLong())
                    }
                } else {
                    binding.radioGroup.check(oldId)
                }
            }
        }

        binding.osUsageStatsTextExportButton.isEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        binding.osUsageStatsTextExportButton.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                try {
                    startActivityForResult(
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("text/json")
                            .putExtra(Intent.EXTRA_TITLE, "timelimit-usage-stats-export.json"),
                        REQ_EXPORT_TEXT
                    )
                } catch (ex: Exception) {
                    Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }

        return binding.root
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromNullableValue("${getString(R.string.diagnose_fga_title)} < ${getString(R.string.about_diagnose_title)} < ${getString(R.string.main_tab_overview)}")

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_EXPORT_TEXT) {
            if (resultCode == Activity.RESULT_OK) {
                val context = requireContext().applicationContext

                Thread {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        throw RuntimeException("unsupported os version")
                    }

                    try {
                        val now = System.currentTimeMillis()
                        val service = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                        val currentData = service.queryEvents(now - InstanceIdForegroundAppHelper.START_QUERY_INTERVAL, now)
                        val reader = DirectUsageStatsReader(currentData)

                        try {
                            JsonWriter(
                                BufferedWriter(
                                    OutputStreamWriter(
                                        context.contentResolver.openOutputStream(
                                            data!!.data!!
                                        )!!
                                    )
                                )
                            ).use { writer ->
                                writer.setIndent("  ")

                                writer.beginArray()

                                while (reader.loadNextEvent()) {
                                    writer.beginObject()

                                    writer.name("timestamp").value(reader.timestamp)
                                    writer.name("type").value(reader.eventType)
                                    writer.name("packageName").value(reader.packageName)
                                    writer.name("className").value(reader.className)

                                    val instanceId = try { reader.instanceId } catch (ex: Exception) { null }

                                    if (instanceId != null) writer.name("instanceId").value(instanceId)

                                    writer.endObject()
                                }

                                writer.endArray()
                            }
                        } finally {
                            reader.free()
                        }

                        Threads.mainThreadHandler.post {
                            Toast.makeText(context, R.string.diagnose_fga_export_toast_done, Toast.LENGTH_SHORT).show()
                        }
                    } catch (ex: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.w(LOG_TAG, "could not do export", ex)
                        }

                        Threads.mainThreadHandler.post {
                            Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        } else super.onActivityResult(requestCode, resultCode, data)
    }
}
