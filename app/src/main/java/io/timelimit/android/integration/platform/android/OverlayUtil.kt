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
package io.timelimit.android.integration.platform.android

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.AppOpsManagerCompat
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.BlockingOverlayBinding
import io.timelimit.android.integration.platform.RuntimePermissionStatus

class OverlayUtil(private var application: Application) {
    private val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val appsOpsManager = application.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val systemOverlayOp = AppOpsManagerCompat.permissionToOp(Manifest.permission.SYSTEM_ALERT_WINDOW)
    private var currentView: BlockingOverlayBinding? = null

    fun show() {
        if (currentView != null) {
            return
        }

        if (getOverlayPermissionStatus(false) == RuntimePermissionStatus.NotGranted) {
            return
        }

        val view = BlockingOverlayBinding.inflate(
                LayoutInflater.from(ContextThemeWrapper(application, R.style.AppTheme))
        )

        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view.root, params)
        currentView = view

        Threads.mainThreadHandler.postDelayed({
            view.showWarningMessage = true
        }, 2000)
    }

    fun hide() {
        if (currentView == null) {
            return
        }

        windowManager.removeView(currentView!!.root)
        currentView = null
    }

    fun setBlockedElement(blockedElement: String) {
        currentView?.let { view ->
            if (view.blockedElement != blockedElement) {
                view.blockedElement = blockedElement
            }
        }
    }

    fun isOverlayShown() = currentView?.root?.isShown ?: false

    fun getOverlayPermissionStatus(strictChecking: Boolean) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        if (checkAppOp(strictChecking) || Settings.canDrawOverlays(application))
            RuntimePermissionStatus.Granted
        else
            RuntimePermissionStatus.NotGranted
    else
        RuntimePermissionStatus.NotRequired

    private fun checkAppOp(strictChecking: Boolean): Boolean {
        if (systemOverlayOp == null) return false

        val mode1 = AppOps.getOpMode(systemOverlayOp, appsOpsManager, application)

        if (mode1 != AppOps.Mode.Unknown) {
            return mode1 == AppOps.Mode.Allowed
        }

        val mode2 = appsOpsManager.checkOpNoThrow(systemOverlayOp, Process.myUid(), application.packageName)

        return if (strictChecking) mode2 == AppOpsManager.MODE_ALLOWED
        else mode2 == AppOpsManager.MODE_ALLOWED || mode2 == AppOpsManager.MODE_IGNORED
    }
}