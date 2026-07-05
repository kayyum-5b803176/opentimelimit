/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.collection.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.runAsyncExpectForever
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.AppActivity
import io.timelimit.android.extensions.registerNotExportedReceiver
import io.timelimit.android.integration.platform.*
import io.timelimit.android.integration.platform.android.foregroundapp.ForegroundAppHelper
import io.timelimit.android.ui.homescreen.HomescreenActivity
import io.timelimit.android.ui.lock.LockActivity
import io.timelimit.android.ui.manage.device.manage.permission.AdbDeviceAdminDialogFragment
import io.timelimit.android.ui.manage.device.manage.permission.AdbUsageStatsDialogFragment
import io.timelimit.android.ui.manage.device.manage.permission.InformAboutDeviceOwnerDialogFragment
import io.timelimit.android.ui.manage.device.manage.permission.PermissionInfoConfirmDialog
import io.timelimit.android.ui.manipulation.AnnoyActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.system.exitProcess


class AndroidIntegration(context: Context): PlatformIntegration(maximumProtectionLevel) {
    companion object {
        private const val LOG_TAG = "AndroidIntegration"

        val maximumProtectionLevel: ProtectionLevel

        init {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                maximumProtectionLevel = ProtectionLevel.DeviceOwner
            } else {
                maximumProtectionLevel = ProtectionLevel.PasswordDeviceAdmin
            }
        }
    }

    private val context = context.applicationContext
    private val policyManager = this.context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val foregroundAppHelper = ForegroundAppHelper.with(this.context)
    private val activityManager = this.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val notificationManager = this.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val deviceAdmin = ComponentName(context.applicationContext, AdminReceiver::class.java)
    private val overlay = OverlayUtil(context as Application)
    private val battery = BatteryStatusUtil(context)
    private val lowBatteryBlockerSettings = LowBatteryBlockerSettings.with(context)
    private val lowBatteryAppBlocker = LowBatteryAppBlocker(context, battery, lowBatteryBlockerSettings, overlay)
    private val connectedNetwork = ConnectedNetworkUtil(context)
    private val muteAudioMutex = Mutex()
    private val screenState = ScreenStateUtil(context)
    // isLowRamDevice is a static device property - cache it instead of doing an
    // ActivityManager round-trip on every permission status query
    private val isLowRamDevice: Boolean by lazy { activityManager.isLowRamDevice }

    // battery optimization: push-based wake-ups for the background loop, so it can
    // sleep long while nothing happens instead of polling quickly at all times
    private val backgroundLoopWakeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val mediaSessionChangeWatcher = MediaSessionChangeWatcher(context) { fireBackgroundLoopWake() }

    private fun fireBackgroundLoopWake() {
        backgroundLoopWakeListeners.forEach { it() }
    }

    override fun registerBackgroundLoopWakeListener(listener: () -> Unit) {
        backgroundLoopWakeListeners.add(listener)
    }

    init {
        AppsChangeListener.registerBroadcastReceiver(this.context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                installedAppsChangeListener?.run()
            }
        })

        context.registerNotExportedReceiver(object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                systemClockChangeListener?.run()
            }
        }, IntentFilter(Intent.ACTION_TIME_CHANGED))

        // the foreground app changed -> the background loop should re-evaluate
        // blocking right away (this is what keeps blocking snappy although the
        // loop itself ticks slowly)
        AccessibilityService.addForegroundPackageListener { _, _ ->
            fireBackgroundLoopWake()
        }

        // screen state changed -> wake the loop (on screen-off it resets the
        // temporarily allowed apps promptly and then enters its idle mode; on
        // screen-on it resumes blocking/measurement immediately); additionally
        // resync the uptime-based timer queue which may have drifted during deep
        // sleep
        screenState.addListener { isScreenOn ->
            if (isScreenOn) {
                io.timelimit.android.integration.time.RealTimeApi.notifyTimersShouldBeCheckedNow()
            }

            fireBackgroundLoopWake()
        }
    }

    override fun getLocalApps(): Collection<App> {
        return AndroidIntegrationApps.getLocalApps(context)
    }

    override fun getLocalAppPackageNames(): List<String> = context.packageManager.getInstalledApplications(0).map { it.packageName }

    override fun getLocalAppActivities(deviceId: String): Collection<AppActivity> {
        return AndroidIntegrationApps.getLocalAppActivities(deviceId, context)
    }

    override fun getLocalAppTitle(packageName: String): String? {
        return AndroidIntegrationApps.getAppTitle(packageName, context)
    }

    override fun getLauncherAppPackageName(): String? {
        return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .resolveActivity(context.packageManager)?.packageName
    }

    override fun getAppIcon(packageName: String): Drawable? {
        return AndroidIntegrationApps.getAppIcon(packageName, context)
    }

    private val isSystemImageAppCache = object: LruCache<String, Boolean>(8) {
        override fun create(key: String): Boolean? = try {
            val appInfo: ApplicationInfo = context.packageManager.getApplicationInfo(key, 0)

            appInfo.flags and ApplicationInfo.FLAG_SYSTEM == ApplicationInfo.FLAG_SYSTEM
        } catch (ex: PackageManager.NameNotFoundException) {
            null
        }
    }

    override fun isSystemImageApp(packageName: String): Boolean = isSystemImageAppCache.get(packageName) ?: false

    override fun getCurrentProtectionLevel(): ProtectionLevel {
        return AdminStatus.getAdminStatus(context, policyManager)
    }

    override suspend fun getForegroundApps(queryInterval: Long, experimentalFlags: Long): Set<ForegroundApp> = foregroundAppHelper.getForegroundApps(queryInterval, experimentalFlags)

    override fun getForegroundAppPermissionStatus(): RuntimePermissionStatus {
        return foregroundAppHelper.getPermissionStatus()
    }

    override fun getMusicPlaybackPackage(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getNotificationAccessPermissionStatus() == NewPermissionStatus.Granted) {
                // opportunistically (re)register the push-based playback change
                // watcher; rate-limited internally, effectively free once registered
                mediaSessionChangeWatcher.ensureRegistered()

                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val sessions = manager.getActiveSessions(ComponentName(context, NotificationListener::class.java))

                return sessions.find { isPlaying(it) }?.packageName
            }
        }

        return null
    }

    override fun showOverlayMessage(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    override fun getDrawOverOtherAppsPermissionStatus(strictChecking: Boolean): RuntimePermissionStatus = overlay.getOverlayPermissionStatus(strictChecking)

    override fun getNotificationAccessPermissionStatus(): NewPermissionStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (isLowRamDevice) {
                return NewPermissionStatus.NotSupported
            } else if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                return NewPermissionStatus.Granted
            } else {
                return NewPermissionStatus.NotGranted
            }
        } else {
            return NewPermissionStatus.NotSupported
        }
    }

    override fun isAccessibilityServiceEnabled(): Boolean {
        val service = context.packageName + "/" + AccessibilityService::class.java.canonicalName

        val accessibilityEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (ex: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled == 1) {
            val enabledServicesString = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

            if (!enabledServicesString.isNullOrEmpty()) {
                if (enabledServicesString.split(":").contains(service)) {
                    return true
                }
            }
        }

        return false
    }

    override fun trySetLockScreenPassword(password: String): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "set password")
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                if (password.isBlank()) {
                    return policyManager.resetPassword("", 0)
                } else if (policyManager.resetPassword(password, DevicePolicyManager.RESET_PASSWORD_REQUIRE_ENTRY)) {
                    policyManager.lockNow()

                    return true
                }
            } catch (ex: SecurityException) {
                if (BuildConfig.DEBUG) {
                    Log.w(LOG_TAG, "error setting password", ex)
                }
            }
        }

        return false
    }

    private var lastAppStatusMessage: AppStatusMessage? = null
    private var appStatusMessageChannel = Channel<AppStatusMessage?>(capacity = Channel.CONFLATED)

    override fun setAppStatusMessage(message: AppStatusMessage?) {
        if (lastAppStatusMessage != message) {
            lastAppStatusMessage = message
            appStatusMessageChannel.trySend(message)
        }
    }

    init {
        runAsyncExpectForever {
            appStatusMessageChannel.consumeEach { message ->
                BackgroundService.setStatusMessage(message, context)

                delay(200)
            }
        }
    }

    override fun showAppLockScreen(currentPackageName: String, currentActivityName: String?) {
        LockActivity.start(context, currentPackageName, currentActivityName)
    }

    override fun showAnnoyScreen() {
        AnnoyActivity.start(context)
    }

    override suspend fun muteAudioIfPossible(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getNotificationAccessPermissionStatus() == NewPermissionStatus.Granted) {
                muteAudioMutex.withLock {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "muteAudioIfPossible($packageName)")
                    }

                    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

                    fun getAppSessions(): List<MediaController> {
                        return manager.getActiveSessions(ComponentName(context, NotificationListener::class.java))
                                .filter { it.packageName == packageName }
                    }

                    fun dispatchKey(sessions: List<MediaController>, key: Int) {
                        sessions.forEach {
                            it.dispatchMediaButtonEvent(KeyEvent(
                                    KeyEvent.ACTION_DOWN,
                                    key
                            ))
                            it.dispatchMediaButtonEvent(KeyEvent(
                                    KeyEvent.ACTION_UP,
                                    key
                            ))
                        }
                    }

                    kotlin.run {
                        val sessions = getAppSessions()

                        if (sessions.find { isPlaying(it) } == null) return true

                        if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "try KEYCODE_MEDIA_STOP") }
                        dispatchKey(sessions, KeyEvent.KEYCODE_MEDIA_STOP)
                    }

                    delay(100)

                    kotlin.run {
                        val sessions = getAppSessions()

                        if (sessions.find { isPlaying(it) } == null) return true

                        if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "try KEYCODE_HEADSETHOOK") }
                        dispatchKey(sessions, KeyEvent.KEYCODE_HEADSETHOOK)
                    }

                    delay(500)

                    kotlin.run {
                        val sessions = getAppSessions()

                        if (sessions.find { isPlaying(it) } == null) return true

                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                        val listener = AudioManager.OnAudioFocusChangeListener {/* ignored */}

                        if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "try audio focus") }
                        if (
                                audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        ) {
                            if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "got audio focus") }
                            delay(100)

                            audioManager.abandonAudioFocus(listener)
                        }
                    }

                    kotlin.run {
                        val sessions = getAppSessions()

                        if (sessions.find { isPlaying(it) } == null) return true
                    }

                    if (BuildConfig.DEBUG) { Log.d(LOG_TAG, "playback still running") }
                }
            }
        }

        return false
    }

    override fun setShowBlockingOverlay(show: Boolean, blockedElement: String?) {
        if (show) {
            overlay.show()
            overlay.setBlockedElement(blockedElement ?: "")
        } else {
            overlay.hide()
        }
    }

    override fun isScreenOn(): Boolean {
        // cached, broadcast-driven value - this is called by the background loop on
        // every round, so it must not be a binder IPC into system_server each time
        return screenState.isScreenOn()
    }

    override fun setShowNotificationToRevokeTemporarilyAllowedApps(show: Boolean) {
        if (show) {
            NotificationChannels.createNotificationChannels(notificationManager, context)

            val actionIntent = PendingIntent.getService(
                    context,
                    PendingIntentIds.REVOKE_TEMPORARILY_ALLOWED,
                    BackgroundActionService.prepareRevokeTemporarilyAllowed(context),
                    PendingIntentIds.PENDING_INTENT_FLAGS
            )

            val notification = NotificationCompat.Builder(context, NotificationChannels.TEMP_ALLOWED_APP)
                    .setSmallIcon(R.drawable.ic_stat_check)
                    .setContentTitle(context.getString(R.string.background_logic_temporarily_allowed_title))
                    .setContentText(context.getString(R.string.background_logic_temporarily_allowed_text))
                    .setContentIntent(actionIntent)
                    .setWhen(0)
                    .setShowWhen(false)
                    .setSound(null)
                    .setOnlyAlertOnce(true)
                    .setLocalOnly(true)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

            notificationManager.notify(NotificationIds.REVOKE_TEMPORARILY_ALLOWED_APPS, notification)
        } else {
            notificationManager.cancel(NotificationIds.REVOKE_TEMPORARILY_ALLOWED_APPS)
        }
    }

    override fun showTimeWarningNotification(title: String, text: String) {
        NotificationChannels.createNotificationChannels(notificationManager, context)

        notificationManager.notify(
                NotificationIds.TIME_WARNING,
                NotificationCompat.Builder(context, NotificationChannels.TIME_WARNING)
                        .setSmallIcon(R.drawable.ic_stat_timelapse)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setLocalOnly(true)
                        .setAutoCancel(false)
                        .setOngoing(false)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()
        )
    }

    override fun showExtraTimeStartedNotification(categoryId: String, categoryTitle: String) {
        NotificationChannels.createNotificationChannels(notificationManager, context)

        notificationManager.notify(
            categoryId,
            NotificationIds.EXTRA_TIME_STARTED,
            NotificationCompat.Builder(context, NotificationChannels.EXTRA_TIME_STARTED)
                .setSmallIcon(R.drawable.ic_stat_timelapse)
                .setContentTitle(context.getString(R.string.notification_extra_time_started))
                .setContentText(categoryTitle)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setLocalOnly(true)
                .setAutoCancel(false)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    override fun disableDeviceAdmin() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                setEnableSystemLockdown(false)
                policyManager.clearDeviceOwnerApp(context.packageName)
            }
        }

        if (policyManager.isAdminActive(deviceAdmin)) {
            policyManager.removeActiveAdmin(deviceAdmin)
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun setSuspendedApps(packageNames: List<String>, suspend: Boolean): List<String> {
        if (
                (getCurrentProtectionLevel() == ProtectionLevel.DeviceOwner) &&
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        ) {
            val failedApps = policyManager.setPackagesSuspended(
                    deviceAdmin,
                    packageNames.toTypedArray(),
                    suspend
            )

            return packageNames.filterNot { failedApps.contains(it) }
        } else {
            return emptyList()
        }
    }

    override fun setEnableSystemLockdown(enableLockdown: Boolean): Boolean {
        return if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                policyManager.isDeviceOwnerApp(context.packageName)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                policyManager.setBackupServiceEnabled(deviceAdmin, true)
            }

            if (enableLockdown) {
                // disable problematic features
                policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_ADD_USER)
                policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_FACTORY_RESET)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    policyManager.addUserRestriction(deviceAdmin, UserManager.DISALLOW_SAFE_BOOT)

                    policyManager.getPermissionGrantState(
                        deviceAdmin,
                        context.packageName,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ).let {
                        if (it == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT) {
                            policyManager.setPermissionGrantState(
                                deviceAdmin,
                                context.packageName,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                )
                                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                                else
                                    DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                            )
                        }
                    }

                    policyManager.setPermissionGrantState(
                        deviceAdmin,
                        context.packageName,
                        Manifest.permission.CALL_PHONE,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    policyManager.setPermissionGrantState(
                        deviceAdmin,
                        context.packageName,
                        Manifest.permission.POST_NOTIFICATIONS,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                }
            } else /* disable lockdown */ {
                // enable problematic features
                policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_ADD_USER)
                policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_FACTORY_RESET)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    policyManager.clearUserRestriction(deviceAdmin, UserManager.DISALLOW_SAFE_BOOT)

                    policyManager.setPermissionGrantState(
                        deviceAdmin,
                        context.packageName,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                    )

                    policyManager.setPermissionGrantState(
                        deviceAdmin,
                        context.packageName,
                        Manifest.permission.CALL_PHONE,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    policyManager.setPermissionGrantState(
                        deviceAdmin,
                        context.packageName,
                        Manifest.permission.POST_NOTIFICATIONS,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                    )
                }

                enableSystemApps()
                stopSuspendingForAllApps()
                setBlockedFeatures(emptySet())
            }

            true
        } else {
            false
        }
    }

    override fun setBlockedFeatures(features: Set<String>): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            policyManager.isDeviceOwnerApp(context.packageName)
        ) AndroidFeatures.applyBlockedFeatures(features, policyManager, deviceAdmin)
        else false
    }

    override fun getFeatures(): List<PlatformFeature> {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
            policyManager.isDeviceOwnerApp(context.packageName)
        ) AndroidFeatures.getFeaturesAssumingDeviceOwnerGranted(context)
        else emptyList()
    }

    private fun enableSystemApps() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        // disabled system apps (all apps - enabled apps)
        val allApps = context.packageManager.getInstalledApplications(
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1)
                    PackageManager.GET_UNINSTALLED_PACKAGES
                else
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
        )
        val enabledAppsPackages = context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()

        allApps
                .asSequence()
                .filterNot { enabledAppsPackages.contains(it.packageName) }
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
                .map { it.packageName }
                .forEach { policyManager.enableSystemApp(deviceAdmin, it) }
    }

    override fun stopSuspendingForAllApps() {
        setSuspendedApps(getLocalAppPackageNames(), false)
    }

    override fun setLockTaskPackages(packageNames: List<String>): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                policyManager.setLockTaskPackages(deviceAdmin, packageNames.toTypedArray())

                true
            } else {
                false
            }
        } else {
            false
        }
    }

    override fun getBatteryStatus(): BatteryStatus = battery.status.value!!
    override fun getBatteryStatusLive(): LiveData<BatteryStatus> = battery.status


    override fun setEnableCustomHomescreen(enable: Boolean) {
        val homescreen = ComponentName(context, HomescreenActivity::class.java)

        context.packageManager.setComponentEnabledSetting(
                homescreen,
                if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        )

        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (policyManager.isDeviceOwnerApp(context.packageName)) {
                    policyManager.addPersistentPreferredActivity(
                            deviceAdmin,
                            IntentFilter(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addCategory(Intent.CATEGORY_DEFAULT)
                            },
                            homescreen
                    )
                }
            }
        }
    }


    override fun setForceNetworkTime(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                policyManager.setAutoTimeRequired(deviceAdmin, enable)
            }
        }
    }

    override fun restartApp() {
        Threads.mainThreadHandler.post {
            if (lastAppStatusMessage != null) {
                LockActivity.start(context, BuildConfig.APPLICATION_ID, null)

                if (!BackgroundService.isBackgroundActivityRestricted(context)) {
                    context.startService(Intent(context, BackgroundActionService::class.java))
                }
            }

            Threads.mainThreadHandler.post {
                exitProcess(0)
            }
        }
    }

    override fun getCurrentNetworkId(): NetworkId = connectedNetwork.getNetworkId()


    private fun isPlaying(session: MediaController): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            session.playbackState?.state == PlaybackState.STATE_PLAYING ||
                    session.playbackState?.state == PlaybackState.STATE_FAST_FORWARDING ||
                    session.playbackState?.state == PlaybackState.STATE_REWINDING
        } else false
    }

    override fun canSetOrganizationName(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            policyManager.isDeviceOwnerApp(context.packageName)
        } else false
    }

    override fun setOrganizationName(name: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (policyManager.isDeviceOwnerApp(context.packageName)) {
                return try {
                    policyManager.setOrganizationName(deviceAdmin, name)

                    true
                } catch (ex: SecurityException) {
                    false
                }
            }
        }

        return false
    }

    override fun openSystemPermissionScren(
        activity: FragmentActivity,
        permission: SystemPermission,
        confirmationLevel: SystemPermissionConfirmationLevel
    ): Boolean = when (permission) {
        SystemPermission.DeviceAdmin -> {
            val protectionLevel = getCurrentProtectionLevel()

            if (protectionLevel == ProtectionLevel.None) {
                if (confirmationLevel == SystemPermissionConfirmationLevel.None) {
                    PermissionInfoConfirmDialog.newInstance(SystemPermission.DeviceAdmin)
                        .show(activity.supportFragmentManager)
                } else if (
                    InformAboutDeviceOwnerDialogFragment.shouldShow &&
                    confirmationLevel != SystemPermissionConfirmationLevel.Suggestion
                ) {
                    InformAboutDeviceOwnerDialogFragment().show(activity.supportFragmentManager)
                } else {
                    try {
                        activity.startActivity(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(
                                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                    ComponentName(context!!, AdminReceiver::class.java)
                                )
                        )
                    } catch (ex: Exception) {
                        AdbDeviceAdminDialogFragment().show(activity.supportFragmentManager)
                    }
                }
            } else {
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ex: Exception) {
                    AdbDeviceAdminDialogFragment().show(activity.supportFragmentManager)
                }
            }

            true
        }
        SystemPermission.UsageStats -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (
                foregroundAppHelper.getPermissionStatus() == RuntimePermissionStatus.NotGranted &&
                confirmationLevel == SystemPermissionConfirmationLevel.None
            ) {
                PermissionInfoConfirmDialog.newInstance(SystemPermission.UsageStats)
                    .show(activity.supportFragmentManager)

                true
            } else {
                // According to user reports, some devices open the wrong screen
                // with the Settings.ACTION_USAGE_ACCESS_SETTINGS
                // but using an activity launcher to open this intent works for them.
                // This intent works at regular android too, so try this first
                // and use the "correct" one as fallback.

                try {
                    activity.startActivity(
                        Intent()
                            .setClassName(
                                "com.android.settings",
                                "com.android.settings.Settings\$UsageAccessSettingsActivity"
                            )
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )

                    true
                } catch (ex: Exception) {
                    try {
                        activity.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )

                        true
                    } catch (ex: Exception) {
                        AdbUsageStatsDialogFragment().show(activity.supportFragmentManager)

                        false
                    }
                }
            }
        } else {
            Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

            false
        }
        SystemPermission.Notification -> if (
            getNotificationAccessPermissionStatus() == NewPermissionStatus.NotGranted &&
            confirmationLevel == SystemPermissionConfirmationLevel.None
        ) {
            PermissionInfoConfirmDialog.newInstance(SystemPermission.Notification)
                .show(activity.supportFragmentManager)

            true
        } else {
            try {
                activity.startActivity(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )

                true
            } catch (ex: Exception) {
                Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

                false
            }
        }
        SystemPermission.Overlay -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (
                overlay.getOverlayPermissionStatus(true) == RuntimePermissionStatus.NotGranted &&
                confirmationLevel == SystemPermissionConfirmationLevel.None
            ) {
                PermissionInfoConfirmDialog.newInstance(SystemPermission.Overlay)
                    .show(activity.supportFragmentManager)

                true
            } else {
                try {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context!!.packageName)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )

                    true
                } catch (ex: Exception) {
                    Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

                    false
                }
            }
        } else {
            Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

            false
        }
        SystemPermission.AccessibilityService -> if (
            !isAccessibilityServiceEnabled() &&
            confirmationLevel == SystemPermissionConfirmationLevel.None
        ) {
            PermissionInfoConfirmDialog.newInstance(SystemPermission.AccessibilityService)
                .show(activity.supportFragmentManager)

            true
        } else {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )

                true
            } catch (ex: Exception) {
                Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

                false
            }
        }
    }

    override fun getExitLog(length: Int): List<ExitLogItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, length)
                .map { ExitLogItem.fromApplicationExitInfo(it) }
        } else emptyList()
    }

    override val deviceOwner: DeviceOwnerApi = AndroidDeviceOwnerApi(deviceAdmin, policyManager)
}
