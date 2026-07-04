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
package io.timelimit.android.livedata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.map

fun LiveData<Boolean>.or(other: LiveData<Boolean>): LiveData<Boolean> {
    val l1 = this
    val l2 = other

    return object: MediatorLiveData<Boolean>() {
        init {
            addSource(l1) { update() }
            addSource(l2) { update() }
        }

        fun update() {
            val v1 = l1.value ?: return
            val v2 = l2.value ?: return

            val v = v1 || v2

            if (v != value) value = v
        }
    }
}

fun LiveData<Boolean>.and(other: LiveData<Boolean>): LiveData<Boolean> {
    val l1 = this
    val l2 = other

    return object: MediatorLiveData<Boolean>() {
        init {
            addSource(l1) { update() }
            addSource(l2) { update() }
        }

        fun update() {
            val v1 = l1.value ?: return
            val v2 = l2.value ?: return

            val v = v1 && v2

            if (v != value) value = v
        }
    }
}

fun LiveData<Boolean>.invert(): LiveData<Boolean> = this.map { !it }
