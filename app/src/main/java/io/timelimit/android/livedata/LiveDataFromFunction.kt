/*
 * Open TimeLimit Copyright <C> 2019 Jonas Lochmann
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
package io.timelimit.android.livedata

import androidx.lifecycle.LiveData
import io.timelimit.android.async.Threads

fun <X> liveDataFromFunction(pollInterval: Long = 1000L, function: () -> X): LiveData<X> = object: LiveData<X>() {
    val refresh = Runnable {
        refresh()
    }

    fun refresh() {
        value = function()

        Threads.mainThreadHandler.postDelayed(refresh, pollInterval)
    }

    override fun onActive() {
        super.onActive()

        refresh()
    }

    override fun onInactive() {
        super.onInactive()

        Threads.mainThreadHandler.removeCallbacks(refresh)
    }
}.ignoreUnchanged()
