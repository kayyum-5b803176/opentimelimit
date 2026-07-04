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
package io.timelimit.android.integration.platform.dummy

import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppRecommendation

object DummyApps {
    val taskmanagerLocalApp = App(
            packageName = "com.demo.taskkiller",
            title = "Task-Killer",
            isLaunchable = true,
            recommendation = AppRecommendation.Blacklist
    )

    val launcherLocalApp = App(
            packageName = "com.demo.home",
            title = "Launcher",
            isLaunchable = true,
            recommendation = AppRecommendation.Whitelist
    )

    val messagingLocalApp = App(
            packageName = "com.demo.messaging",
            title = "Messaging",
            isLaunchable = true,
            recommendation = AppRecommendation.None
    )

    val gameLocalApp = App(
            packageName = "com.demo.game",
            title = "Game",
            isLaunchable = true,
            recommendation = AppRecommendation.None
    )

    val all = listOf(taskmanagerLocalApp, launcherLocalApp, messagingLocalApp, gameLocalApp)
}
