/*
 * TimeLimit Copyright <C> 2019 - 2021 Jonas Lochmann
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
import io.timelimit.android.util.Option

fun <T1, T2> mergeLiveData(d1: LiveData<T1>, d2: LiveData<T2>): LiveData<Pair<T1?, T2?>> {
    val result = MediatorLiveData<Pair<T1?, T2?>>()
    result.value = Pair(null, null)

    result.addSource(d1) {
        result.value = result.value!!.copy(first = it)
    }

    result.addSource(d2) {
        result.value = result.value!!.copy(second = it)
    }

    return result
}

fun <T1, T2, T3> mergeLiveData(d1: LiveData<T1>, d2: LiveData<T2>, d3: LiveData<T3>): LiveData<Triple<T1?, T2?, T3?>> {
    val result = MediatorLiveData<Triple<T1?, T2?, T3?>>()
    result.value = Triple(null, null, null)

    result.addSource(d1) {
        result.value = result.value!!.copy(first = it)
    }

    result.addSource(d2) {
        result.value = result.value!!.copy(second = it)
    }

    result.addSource(d3) {
        result.value = result.value!!.copy(third = it)
    }

    return result
}

fun <T1, T2, T3, T4> mergeLiveData(d1: LiveData<T1>, d2: LiveData<T2>, d3: LiveData<T3>, d4: LiveData<T4>): LiveData<FourTuple<T1?, T2?, T3?, T4?>> {
    val result = MediatorLiveData<FourTuple<T1?, T2?, T3?, T4?>>()
    result.value = FourTuple(null, null, null, null)

    result.addSource(d1) {
        result.value = result.value!!.copy(first = it)
    }

    result.addSource(d2) {
        result.value = result.value!!.copy(second = it)
    }

    result.addSource(d3) {
        result.value = result.value!!.copy(third = it)
    }

    result.addSource(d4) {
        result.value = result.value!!.copy(forth = it)
    }

    return result
}

data class FourTuple<A, B, C, D>(val first: A, val second: B, val third: C, val forth: D)

fun <T1, T2, T3, T4, T5> mergeLiveData(d1: LiveData<T1>, d2: LiveData<T2>, d3: LiveData<T3>, d4: LiveData<T4>, d5: LiveData<T5>): LiveData<FiveTuple<T1?, T2?, T3?, T4?, T5?>> {
    val result = MediatorLiveData<FiveTuple<T1?, T2?, T3?, T4?, T5?>>()
    result.value = FiveTuple(null, null, null, null, null)

    result.addSource(d1) {
        result.value = result.value!!.copy(first = it)
    }

    result.addSource(d2) {
        result.value = result.value!!.copy(second = it)
    }

    result.addSource(d3) {
        result.value = result.value!!.copy(third = it)
    }

    result.addSource(d4) {
        result.value = result.value!!.copy(forth = it)
    }

    result.addSource(d5) {
        result.value = result.value!!.copy(fifth = it)
    }

    return result
}

data class FiveTuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val forth: D, val fifth: E)

fun <T1, T2, T3, T4, T5, T6> mergeLiveData(d1: LiveData<T1>, d2: LiveData<T2>, d3: LiveData<T3>, d4: LiveData<T4>, d5: LiveData<T5>, d6: LiveData<T6>): LiveData<SixTuple<T1?, T2?, T3?, T4?, T5?, T6?>> {
    val result = MediatorLiveData<SixTuple<T1?, T2?, T3?, T4?, T5?, T6?>>()
    result.value = SixTuple(null, null, null, null, null, null)

    result.addSource(d1) {
        result.value = result.value!!.copy(first = it)
    }

    result.addSource(d2) {
        result.value = result.value!!.copy(second = it)
    }

    result.addSource(d3) {
        result.value = result.value!!.copy(third = it)
    }

    result.addSource(d4) {
        result.value = result.value!!.copy(forth = it)
    }

    result.addSource(d5) {
        result.value = result.value!!.copy(fifth = it)
    }

    result.addSource(d6) {
        result.value = result.value!!.copy(sixth = it)
    }

    return result
}

data class SixTuple<A, B, C, D, E, F>(val first: A, val second: B, val third: C, val forth: D, val fifth: E, val sixth: F)

fun <T1, T2> mergeLiveDataWaitForValues(d1: LiveData<T1>, d2: LiveData<T2>): LiveData<Pair<T1, T2>> {
    val result = MediatorLiveData<Pair<T1, T2>>()
    var state = Pair<Option<T1>, Option<T2>>(Option.None(), Option.None())

    fun update() {
        val (a, b) = state

        if (a is Option.Some && b is Option.Some) {
            result.value = Pair(a.value, b.value)
        }
    }

    result.addSource(d1) {
        state = state.copy(first = Option.Some(it))

        update()
    }

    result.addSource(d2) {
        state = state.copy(second = Option.Some(it))

        update()
    }

    return result
}

fun <T1, T2, T3> mergeLiveDataWaitForValues(d1: LiveData<T1>, d2: LiveData<T2>, d3: LiveData<T3>): LiveData<Triple<T1, T2, T3>> {
    val result = MediatorLiveData<Triple<T1, T2, T3>>()
    var state = Triple<Option<T1>, Option<T2>, Option<T3>>(Option.None(), Option.None(), Option.None())

    fun update() {
        val (a, b, c) = state

        if (a is Option.Some && b is Option.Some && c is Option.Some) {
            result.value = Triple(a.value, b.value, c.value)
        }
    }

    result.addSource(d1) {
        state = state.copy(first = Option.Some(it))

        update()
    }

    result.addSource(d2) {
        state = state.copy(second = Option.Some(it))

        update()
    }

    result.addSource(d3) {
        state = state.copy(third = Option.Some(it))

        update()
    }

    return result
}