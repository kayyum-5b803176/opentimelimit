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

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import io.timelimit.android.async.Threads

/**
 * Push-based "something about media playback changed" signal.
 *
 * This watcher is deliberately NOT the source of truth for what is playing -
 * getMusicPlaybackPackage() still asks MediaSessionManager directly when the
 * background loop runs. Its only job is to *wake* the background loop the moment
 * playback starts/stops or sessions appear/disappear, which is what makes it safe
 * for the loop to idle with a long interval while the screen is off: if music
 * starts (e.g. via a Bluetooth headset play button while the screen stays off),
 * the loop is poked immediately instead of only noticing it on its next safety
 * poll. A missed event therefore degrades gracefully - the safety poll still
 * catches it - it can never cause wrong blocking/measurement results.
 *
 * Registration requires notification listener access (same permission the
 * existing getMusicPlaybackPackage() path needs). ensureRegistered() is cheap and
 * rate-limited, so callers can invoke it opportunistically; it retries silently
 * until the permission is there.
 */
class MediaSessionChangeWatcher(context: Context, private val onChange: () -> Unit) {
    companion object {
        private const val REGISTER_RETRY_INTERVAL = 60 * 1000L
    }

    private val appContext = context.applicationContext
    private val handler = Threads.mainThreadHandler

    @Volatile
    private var registered = false
    private var lastRegisterAttempt = 0L
    private var controllersWithCallback: List<MediaController> = emptyList()

    fun ensureRegistered() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (registered) return

        val now = SystemClock.elapsedRealtime()

        if (lastRegisterAttempt != 0L && now - lastRegisterAttempt < REGISTER_RETRY_INTERVAL) return

        lastRegisterAttempt = now

        handler.post { tryRegister() }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun tryRegister() {
        if (registered) return

        try {
            val manager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(appContext, NotificationListener::class.java)

            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, component, handler)
            registered = true

            // attach playback state callbacks to the sessions which already exist
            attachControllerCallbacks(manager.getActiveSessions(component))
        } catch (ex: Exception) {
            // most likely a SecurityException because the notification listener
            // permission is not granted (yet) - stay unregistered and retry later;
            // without that permission audio playback is not detectable anyway, so
            // nothing is lost
        }
    }

    @get:TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private val sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener by lazy {
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachControllerCallbacks(controllers ?: emptyList())

            onChange()
        }
    }

    @get:TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private val controllerCallback: MediaController.Callback by lazy {
        object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                onChange()
            }

            override fun onSessionDestroyed() {
                onChange()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun attachControllerCallbacks(controllers: List<MediaController>) {
        // always called at the main thread (handler is the main thread handler)
        controllersWithCallback.forEach {
            try {
                it.unregisterCallback(controllerCallback)
            } catch (ex: Exception) {
                // the session may be dead already - ignore
            }
        }

        controllersWithCallback = controllers

        controllers.forEach {
            try {
                it.registerCallback(controllerCallback, handler)
            } catch (ex: Exception) {
                // the session may be dead already - ignore
            }
        }
    }
}
