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
package io.timelimit.android.logic

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.timelimit.android.R
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppRecommendation

object DummyApps {
    const val NOT_ASSIGNED_SYSTEM_IMAGE_APP = ".dummy.system_image"
    const val MISSING_PERMISSION_APP = ".dummy.missing_usage_stats_permission"
    const val FEATURE_APP_PREFIX = ".feature."
    const val ACTIVITY_BACKGROUND_AUDIO = ".background_audio"

    fun getTitle(packageName: String, context: Context): String? = when (packageName) {
        NOT_ASSIGNED_SYSTEM_IMAGE_APP -> context.getString(R.string.dummy_app_unassigned_system_image_app)
        else -> null
    }

    fun getIcon(packageName: String, context: Context): Drawable? {
        val usePlaceholder = packageName == NOT_ASSIGNED_SYSTEM_IMAGE_APP || packageName.startsWith(FEATURE_APP_PREFIX)

        return if (usePlaceholder) ContextCompat.getDrawable(context, R.mipmap.ic_system_app)
        else null
    }

    fun getApps(context: Context): List<App> = listOf(
        App(
            packageName = NOT_ASSIGNED_SYSTEM_IMAGE_APP,
            title = getTitle(NOT_ASSIGNED_SYSTEM_IMAGE_APP, context)!!,
            isLaunchable = false,
            recommendation = AppRecommendation.None
        )
    )

    fun forFeature(id: String, title: String) = App(
        packageName = FEATURE_APP_PREFIX + id,
        title = title,
        isLaunchable = false,
        recommendation = AppRecommendation.None
    )
}