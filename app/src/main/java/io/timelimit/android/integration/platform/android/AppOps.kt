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
package io.timelimit.android.integration.platform.android

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import java.lang.ClassCastException
import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@SuppressLint("PrivateApi")
object AppOps {
    internal class ReflectionData (
        val getOpsForPackage: Method,
        val getOps: Method,
        val getMode: Method
    )

    private val reflectionData by lazy {
        try {
            val getOpsForPackage = AppOpsManager::class.java.getMethod("getOpsForPackage", Int::class.java, String::class.java, Array<String>::class.java)
            val packageOpsClass = Class.forName("android.app.AppOpsManager\$PackageOps")
            val getOps = packageOpsClass.getMethod("getOps")
            val opEntryClass = Class.forName("android.app.AppOpsManager\$OpEntry")
            val getMode = opEntryClass.getMethod("getMode")

            ReflectionData(
                getOpsForPackage = getOpsForPackage,
                getOps = getOps,
                getMode = getMode
            )
        } catch (ex: ReflectiveOperationException) {
            null
        }
    }

    private val setMode by lazy { try {
        AppOpsManager::class.java.getMethod("setMode", String::class.java, Int::class.java, String::class.java, Int::class.java)
    } catch (ex: ReflectiveOperationException) {
        null
    } }

    fun getOpMode(op: String, appOpsManager: AppOpsManager, context: Context): Mode {
        try {
            val reflectionData = reflectionData ?: return Mode.Unknown

            val uid: Int = Process.myUid()
            val pkg: String = context.packageName
            val ops: Array<String> = arrayOf(op)

            val packageOpsList =
                reflectionData.getOpsForPackage.invoke(appOpsManager, uid, pkg, ops) as List<*>

            val packageOpsItem = packageOpsList.singleOrNull() ?: return Mode.Unknown

            val opEntryList = reflectionData.getOps.invoke(packageOpsItem) as List<*>

            val opEntryItem = opEntryList.singleOrNull() ?: return Mode.Unknown

            val mode = reflectionData.getMode.invoke(opEntryItem) as Int

            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> Mode.Allowed
                AppOpsManager.MODE_DEFAULT -> Mode.Default
                AppOpsManager.MODE_IGNORED -> Mode.Ignored
                AppOpsManager.MODE_ERRORED -> Mode.Blocked
                else -> Mode.Unknown
            }
        } catch (ex: ReflectiveOperationException) {
            return Mode.Unknown
        } catch (ex: ClassCastException) {
            return Mode.Unknown
        } catch (ex: IllegalArgumentException) {
            return Mode.Unknown
        } catch (ex: InvocationTargetException) {
            if (ex.cause is SecurityException) return Mode.Unknown
            else throw ex
        }
    }

    fun setMode(op: String, appOpsManager: AppOpsManager, context: Context, mode: Mode) {
        val setMode = setMode

        if (setMode == null) throw SecurityException("blocked by the OS")

        val realMode = when (mode) {
            Mode.Allowed -> AppOpsManager.MODE_ALLOWED
            Mode.Default -> AppOpsManager.MODE_DEFAULT
            Mode.Ignored -> AppOpsManager.MODE_IGNORED
            Mode.Blocked -> AppOpsManager.MODE_ERRORED
            else -> return
        }

        try {
            setMode.invoke(appOpsManager, op, Process.myUid(), context.packageName, realMode)
        } catch (ex: InvocationTargetException) {
            ex.cause?.let { throw it }

            throw ex
        }
    }

    enum class Mode {
        Unknown,
        Allowed,
        Blocked,
        Default,
        Ignored
    }
}