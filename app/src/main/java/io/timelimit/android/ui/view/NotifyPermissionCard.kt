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
package io.timelimit.android.ui.view

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import io.timelimit.android.databinding.NotifyPermissionCardBinding

object NotifyPermissionCard {
    enum class Status {
        Unknown,
        WaitingForInteraction,
        SkipGrant,
        Granted
    }

    fun bind(status: Status, view: NotifyPermissionCardBinding) {
        view.showGrantButton = status != Status.Granted
        view.showGrantedMessage = status == Status.Granted
        view.showSkipButton = status == Status.WaitingForInteraction
    }

    fun bind(listener: Listener, view: NotifyPermissionCardBinding) {
        view.grantButton.setOnClickListener { listener.onGrantClicked() }
        view.skipButton.setOnClickListener { listener.onSkipClicked() }
    }

    fun updateStatus(status: Status, context: Context) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (context.getSystemService<NotificationManager>()!!.areNotificationsEnabled()) Status.Granted
        else if (status == Status.SkipGrant) Status.SkipGrant
        else Status.WaitingForInteraction
    } else Status.Granted

    fun canProceed(status: Status) = when (status) {
        Status.Unknown -> false
        Status.WaitingForInteraction -> false
        Status.SkipGrant -> true
        Status.Granted -> true
    }

    interface Listener {
        fun onGrantClicked()
        fun onSkipClicked()
    }
}