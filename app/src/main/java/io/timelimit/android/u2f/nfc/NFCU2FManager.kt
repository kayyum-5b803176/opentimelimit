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
package io.timelimit.android.u2f.nfc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import io.timelimit.android.BuildConfig
import io.timelimit.android.extensions.registerNotExportedReceiver
import io.timelimit.android.integration.platform.android.PendingIntentIds
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.liveDataFromNonNullValue
import io.timelimit.android.u2f.U2fManager
import io.timelimit.android.u2f.util.U2FId

class NFCU2FManager (val parent: U2fManager, context: Context) {
    companion object {
        private const val LOG_TAG = "NFCU2FManager"
    }

    private val nfcManager = context.getSystemService<NfcManager>()
    private val nfcAdapter: NfcAdapter? = nfcManager?.defaultAdapter

    private val nfcReceiver = object: BroadcastReceiver() {
        override fun onReceive(p0: Context?, intent: Intent?) {
            try {
                val tagFromIntent: Tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
                else
                    intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return

                val isoDep: IsoDep = IsoDep.get(tagFromIntent) ?: return

                parent.dispatchDeviceFound(NfcU2FDevice(isoDep))
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "could not handle nfc broadcast", ex)
                }
            }
        }
    }
    private val nfcReceiverAction = U2FId.generate()
    private val nfcReceiverIntent = PendingIntent.getBroadcast(
        context,
        PendingIntentIds.U2F_NFC_DISCOVERY,
        Intent(nfcReceiverAction),
        PendingIntentIds.PENDING_INTENT_FLAGS_ALLOW_MUTATION
    )

    val status: LiveData<NfcStatus> = if (nfcAdapter == null)
        liveDataFromNonNullValue(NfcStatus.Unsupported)
    else
        liveDataFromFunction { if (nfcAdapter.isEnabled) NfcStatus.Ready else NfcStatus.Disabled }

    init { context.registerNotExportedReceiver(nfcReceiver, IntentFilter(nfcReceiverAction)) }

    fun setupActivity(activity: FragmentActivity) {
        if (nfcAdapter != null) {
            activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "enableForegroundDispatch")
                    }

                    nfcAdapter.enableForegroundDispatch(
                        activity,
                        nfcReceiverIntent,
                        arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)),
                        arrayOf(arrayOf<String>(IsoDep::class.java.name))
                    )
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "disableForegroundDispatch")
                    }

                    nfcAdapter.disableForegroundDispatch(activity)
                }
            })
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "skip nfc setup because not supported")
            }
        }
    }
}