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
package io.timelimit.android.ui.manage.parent.u2fkey.add

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.livedata.castDown
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddParentU2FKey
import io.timelimit.android.u2f.U2FApplicationId
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.u2f.protocol.U2FRequest
import io.timelimit.android.u2f.protocol.login
import io.timelimit.android.u2f.protocol.register
import io.timelimit.android.u2f.util.U2FException
import kotlinx.coroutines.delay
import java.security.SecureRandom

class AddU2FModel(application: Application): AndroidViewModel(application), U2fManager.DeviceFoundListener {
    companion object {
        private const val LOG_TAG = "AddU2FModel"
    }

    private var userId: String? = null
    private val statusInternal = MutableLiveData<Status>().apply { value = Status.WaitingForKey }
    private val logic = DefaultAppLogic.with(application)

    val status = statusInternal.castDown()

    fun init(userId: String) {
        if (this.userId == null) this.userId = userId
    }

    override fun onDeviceFound(device: U2FDevice) {
        if (statusInternal.value == Status.Working || statusInternal.value is Status.Done) return

        statusInternal.value = Status.Working

        runAsync {
            try {
                val applicationId = U2FApplicationId.fromUrl(U2FApplicationId.URL)

                device.connect().use { session ->
                    val currentKeys =
                        Threads.database.executeAndWait { logic.database.u2f().getAllSync() }

                    for (key in currentKeys) {
                        try {
                            session.login(
                                U2FRequest.Login(
                                    mode = U2FRequest.Login.Mode.CheckOnly,
                                    challenge = ByteArray(32).also { SecureRandom().nextBytes(it) },
                                    applicationId = applicationId,
                                    keyHandle = key.keyHandle
                                )
                            )
                        } catch (ex: U2FException.BadKeyHandleException) {
                            continue
                        } catch (ex: U2FException.UserInteractionRequired) {
                            statusInternal.value = Status.AlreadyLinked

                            return@runAsync
                        }
                    }

                    val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }

                    while (true) {
                        try {
                            val registerResponse = session.register(
                                U2FRequest.Register(
                                    challenge = challenge,
                                    applicationId = applicationId
                                )
                            )

                            statusInternal.value = Status.Done(
                                AddParentU2FKey(
                                    keyHandle = registerResponse.keyHandle,
                                    publicKey = registerResponse.publicKey
                                )
                            )

                            break
                        } catch (ex: U2FException.UserInteractionRequired) {
                            if (statusInternal.value != Status.NeedsUserInteraction) {
                                statusInternal.value = Status.NeedsUserInteraction
                            }

                            delay(50)
                        }
                    }
                }
            } catch (ex: U2FException.DisconnectedException) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "connection interrupted", ex)
                }

                statusInternal.value = Status.ConnectionInterrupted
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "request failed", ex)
                }

                statusInternal.value = Status.RequestFailed
            }
        }
    }

    sealed class Status {
        object WaitingForKey: Status()
        object Working: Status()
        object ConnectionInterrupted: Status()
        object RequestFailed: Status()
        object AlreadyLinked: Status()
        object NeedsUserInteraction: Status()
        data class Done(val action: AddParentU2FKey, var commited: Boolean = false): Status()
    }
}