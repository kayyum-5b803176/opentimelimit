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
package io.timelimit.android.ui.main

import android.app.Activity
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.and
import io.timelimit.android.livedata.invert

object AuthenticationFab {
    private const val LOG_TAG = "AuthenticationFab"

    fun manageAuthenticationFab(
            fab: FloatingActionButton,
            shouldHighlight: MutableLiveData<Boolean>,
            authenticatedUser: LiveData<User?>,
            doesSupportAuth: LiveData<Boolean>,
            fragment: Fragment
    ) = manageAuthenticationFab(
            fab = fab,
            shouldHighlight = shouldHighlight,
            authenticatedUser = authenticatedUser,
            doesSupportAuth = doesSupportAuth,
            activity = fragment.requireActivity(),
            viewLifecycleOwner = fragment
    )

    fun manageAuthenticationFab(
            fab: FloatingActionButton,
            shouldHighlight: MutableLiveData<Boolean>,
            authenticatedUser: LiveData<User?>,
            doesSupportAuth: LiveData<Boolean>,
            activity: FragmentActivity
    ) = manageAuthenticationFab(
            fab = fab,
            shouldHighlight = shouldHighlight,
            authenticatedUser = authenticatedUser,
            doesSupportAuth = doesSupportAuth,
            activity = activity,
            viewLifecycleOwner = activity
    )

    private fun manageAuthenticationFab(
            fab: FloatingActionButton,
            shouldHighlight: MutableLiveData<Boolean>,
            authenticatedUser: LiveData<User?>,
            doesSupportAuth: LiveData<Boolean>,
            activity: Activity,
            viewLifecycleOwner: LifecycleOwner
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "start managing FAB instance")
        }

        var tapTargetView: TapTargetView? = null

        val highlightObserver = Observer<Boolean> {
            if (it == true) {
                if (tapTargetView == null && fab.isAttachedToWindow) {
                    tapTargetView = MyTapTargetView.showFor(activity,
                            TapTarget.forView(fab, activity.getString(R.string.authentication_required_overlay_title), activity.getString(R.string.authentication_required_overlay_text))
                                    .cancelable(true)
                                    .tintTarget(true)
                                    .transparentTarget(false)
                                    .outerCircleColor(R.color.colorAccent)
                                    .textColor(R.color.white)
                                    .targetCircleColor(R.color.white)
                                    .icon(ContextCompat.getDrawable(activity, R.drawable.ic_lock_open_white_24dp)),
                            object : TapTargetView.Listener() {
                                override fun onTargetClick(view: TapTargetView) {
                                    super.onTargetClick(view)

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "target clicked")
                                    }

                                    tapTargetView = null
                                    fab.callOnClick()
                                }

                                override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                                    super.onTargetDismissed(view, userInitiated)

                                    if (BuildConfig.DEBUG) {
                                        Log.d(LOG_TAG, "target dismissed")
                                    }

                                    tapTargetView = null
                                    shouldHighlight.value = false
                                }
                            })
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "dismissing tap target view; view == null: ${tapTargetView == null}")
                }

                tapTargetView?.let {
                    if (it.isLaidOut) {
                        it.dismiss(false)
                    }
                }

                tapTargetView = null
            }
        }

        val isParentAuthenticated = authenticatedUser.map { it != null && it.type == UserType.Parent }
        val shouldShowFab = (isParentAuthenticated.invert()).and(doesSupportAuth)

        val showFabObserver = Observer<Boolean> {
            if (it == true) {
                fab.show()
            } else {
                fab.hide()
            }
        }

        shouldHighlight.observe(viewLifecycleOwner, highlightObserver)
        shouldShowFab.observe(viewLifecycleOwner, showFabObserver)
    }
}
