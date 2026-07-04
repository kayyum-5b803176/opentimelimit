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
package io.timelimit.android.integration.platform

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi

data class ExitLogItem (
    val timestamp: Long,
    val reason: ExitReason,
    val description: String?
) {
    companion object {
        @RequiresApi(Build.VERSION_CODES.R)
        fun fromApplicationExitInfo(item: ApplicationExitInfo): ExitLogItem = ExitLogItem(
            timestamp = item.timestamp,
            reason = ExitReasonConverter.fromApplicationExitInfo(item),
            description = item.description
        )
    }
}

enum class ExitReason {
    AppNotResponding,
    Crash,
    CrashNative,
    DependencyDied,
    TooMuchRessourceUsage,
    Self,
    InitFailure,
    LowMemory,
    SystemOther,
    PermissionChange,
    Signaled,
    SystemUnknown,
    UserRequest,
    UserStopped,
    Unknown
}

object ExitReasonConverter {
    fun fromApplicationExitInfo(input: ApplicationExitInfo): ExitReason = when (input.reason) {
        ApplicationExitInfo.REASON_ANR -> ExitReason.AppNotResponding
        ApplicationExitInfo.REASON_CRASH -> ExitReason.Crash
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitReason.CrashNative
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> ExitReason.DependencyDied
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> ExitReason.TooMuchRessourceUsage
        ApplicationExitInfo.REASON_EXIT_SELF -> ExitReason.Self
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ExitReason.InitFailure
        ApplicationExitInfo.REASON_LOW_MEMORY -> ExitReason.LowMemory
        ApplicationExitInfo.REASON_OTHER -> ExitReason.SystemOther
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> ExitReason.PermissionChange
        ApplicationExitInfo.REASON_SIGNALED -> ExitReason.Signaled
        ApplicationExitInfo.REASON_UNKNOWN -> ExitReason.SystemUnknown
        ApplicationExitInfo.REASON_USER_REQUESTED -> ExitReason.UserRequest
        ApplicationExitInfo.REASON_USER_STOPPED -> ExitReason.UserStopped
        else -> ExitReason.Unknown
    }
}