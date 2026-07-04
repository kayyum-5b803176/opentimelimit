/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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

package io.timelimit.android.ui.setup.parentmode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.executeAndWait
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.Curve25519
import io.timelimit.android.data.Database

class SetupParentmodeDialogModel: ViewModel() {
    private var didInit = false
    private val hadSuccess = MutableLiveData<Boolean>()

    fun init(database: Database): LiveData<Boolean> {
        if (!didInit) {
            didInit = true

            runAsync {
                val keys = Threads.crypto.executeAndWait { Curve25519.generateKeyPair() }
                val ok = Threads.database.executeAndWait {
                    database.runInTransaction {
                        if (database.config().getOwnDeviceIdSync() != null) {
                            false
                        } else if (database.config().getParentModeKeySync() != null) {
                            true
                        } else {
                            database.config().setParentModeKeySync(keys)

                            true
                        }
                    }
                }

                hadSuccess.value = ok
            }
        }

        return hadSuccess
    }
}