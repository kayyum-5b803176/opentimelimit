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
package io.timelimit.android.ui.homescreen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.timelimit.android.BuildConfig

object HomescreenUtil {
    fun openHomescreenIntent() = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    // "com.android.settings" contains a fallback homescreen which does nothing - no good option here
    fun launcherOptions(context: Context) = context.packageManager.queryIntentActivities(openHomescreenIntent(), 0)
            .filterNot { it.activityInfo.packageName == BuildConfig.APPLICATION_ID || it.activityInfo.packageName == "com.android.settings" }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
}