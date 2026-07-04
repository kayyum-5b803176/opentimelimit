/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.logic

import io.timelimit.android.integration.platform.PlatformIntegration

class QueryAppTitleCache(val platformIntegration: PlatformIntegration) {
    private var lastPackageName: String? = null
    private var lastAppTitle: String? = null

    fun query(packageName: String): String {
        if (packageName == lastPackageName) {
            return lastAppTitle!!
        } else {
            val title = platformIntegration.getLocalAppTitle(packageName)

            lastAppTitle = when {
                title != null -> title
                else -> packageName
            }
            lastPackageName = packageName

            return lastAppTitle!!
        }
    }
}
