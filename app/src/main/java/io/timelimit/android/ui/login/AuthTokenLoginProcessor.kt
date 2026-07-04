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
package io.timelimit.android.ui.login

import android.util.Log
import android.widget.Toast
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.u2f.U2FApplicationId
import io.timelimit.android.u2f.U2FSignatureValidation
import io.timelimit.android.u2f.protocol.U2FDevice
import io.timelimit.android.u2f.protocol.U2FRequest
import io.timelimit.android.u2f.protocol.login
import io.timelimit.android.u2f.util.U2FException
import io.timelimit.android.u2f.util.U2FThread
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticatedUser
import java.security.SecureRandom

object AuthTokenLoginProcessor {
    private const val LOG_TAG = "AuthTokenLoginProcessor"

    fun process(device: U2FDevice, model: ActivityViewModel) {
        if (model.isParentAuthenticated()) return

        fun toast(message: Int) = Toast.makeText(model.logic.context, message, Toast.LENGTH_SHORT).show()
        fun toast(message: String) = Toast.makeText(model.logic.context, message, Toast.LENGTH_SHORT).show()

        runAsync {
            try {
                device.connect().use { session ->
                    val keys = Threads.database.executeAndWait { model.logic.database.u2f().getAllSync() }
                    val random = SecureRandom()
                    val applicationId = U2FApplicationId.fromUrl(U2FApplicationId.URL)

                    for (key in keys) {
                        if (BuildConfig.DEBUG) {
                            Log.d(LOG_TAG, "try key $key")
                        }

                        val challenge = ByteArray(32).also { random.nextBytes(it) }

                        try {
                            val response = session.login(
                                U2FRequest.Login(
                                    mode = U2FRequest.Login.Mode.DoNotEnforcePresence,
                                    challenge = challenge,
                                    applicationId = applicationId,
                                    keyHandle = key.keyHandle
                                )
                            )

                            if (BuildConfig.DEBUG) {
                                Log.d(LOG_TAG, "got response $response")
                            }

                            val signatureValid = U2FThread.crypto.executeAndWait {
                                U2FSignatureValidation.validate(
                                    applicationId = applicationId,
                                    challenge = challenge,
                                    response = response,
                                    publicKey = key.publicKey
                                )
                            }

                            if (!signatureValid) {
                                toast(R.string.u2f_login_error_invalid)

                                break
                            }

                            val userEntry = Threads.database.executeAndWait {
                                if (
                                    model.logic.database.u2f().updateCounter(
                                        parentUserId = key.userId,
                                        keyHandle = key.keyHandle,
                                        publicKey = key.publicKey,
                                        counter = response.counter.toLong()
                                    ) > 0
                                ) {
                                    model.logic.database.user().getUserByIdSync(key.userId)!!
                                } else null
                            }

                            if (userEntry == null) {
                                toast(R.string.u2f_login_error_invalid)

                                break
                            }

                            val allowLoginStatus = Threads.database.executeAndWait {
                                AllowUserLoginStatusUtil.calculateSync(
                                    logic = model.logic,
                                    userId = userEntry.id
                                )
                            }

                            val shouldSignIn = allowLoginStatus is AllowUserLoginStatus.Allow

                            if (!shouldSignIn) {
                                toast(LoginDialogFragmentModel.formatAllowLoginStatusError(allowLoginStatus, model.getApplication()))

                                return@runAsync
                            }

                            model.setAuthenticatedUser(
                                AuthenticatedUser.LocalAuth.U2f(
                                    userId = key.userId
                                )
                            )

                            return@runAsync // no need to try more
                        } catch (ex: U2FException.BadKeyHandleException) {
                            // ignore and try the next one
                        }
                    }

                    toast(R.string.u2f_login_error_unknown)
                }
            } catch (ex: U2FException.DisconnectedException) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "disconnected", ex)
                }

                toast(R.string.manage_parent_u2f_status_interrupted)
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "error", ex)
                }

                toast(R.string.error_general)
            }
        }
    }
}