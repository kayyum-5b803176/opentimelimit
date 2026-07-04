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
package io.timelimit.android.integration.platform.android.foregroundapp

import android.content.Context
import android.os.Build
import io.timelimit.android.integration.platform.ForegroundApp
import io.timelimit.android.integration.platform.RuntimePermissionStatus

abstract class ForegroundAppHelper {
    abstract suspend fun getForegroundApps(queryInterval: Long, experimentalFlags: Long): Set<ForegroundApp>
    abstract fun getPermissionStatus(): RuntimePermissionStatus

    companion object {
        private val lock = Any()
        private var instance: ForegroundAppHelper? = null

        fun with(context: Context): ForegroundAppHelper {
            if (instance == null) {
                synchronized(lock) {
                    if (instance == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            instance = QForegroundAppHelper(context.applicationContext)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            instance = LollipopForegroundAppHelper(context.applicationContext)
                        } else {
                            instance = CompatForegroundAppHelper(context.applicationContext)
                        }
                    }
                }
            }

            return instance!!
        }
    }
}
