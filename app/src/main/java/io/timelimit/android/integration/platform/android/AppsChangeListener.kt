/*
 * Open TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
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
package io.timelimit.android.integration.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.timelimit.android.extensions.registerNotExportedReceiver

object AppsChangeListener {
    private val changeFilter = IntentFilter()
    private val externalFilter = IntentFilter()

    init {
        changeFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        changeFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        changeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        changeFilter.addDataScheme("package")

        externalFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
        externalFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
    }

    fun registerBroadcastReceiver(context: Context, listener: BroadcastReceiver) {
        context.registerNotExportedReceiver(listener, changeFilter)
        context.registerNotExportedReceiver(listener, externalFilter)
    }
}
