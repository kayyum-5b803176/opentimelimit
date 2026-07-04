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
package io.timelimit.android.ui.manipulation

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.R
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder

class AnnoyUnlockDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "AnnoyUnlockDialogFragment"
        private const val UNLOCK_DURATION = "unlockDuration"

        fun newInstance(duration: UnlockDuration) = AnnoyUnlockDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(UNLOCK_DURATION, duration)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val duration = requireArguments().getSerializable(UNLOCK_DURATION) as UnlockDuration
        val activity = requireActivity() as ActivityViewModelHolder
        val logic = DefaultAppLogic.with(requireContext())

        return AlertDialog.Builder(requireContext(), theme)
            .setMessage(R.string.annoy_unlock_dialog_text)
            .setNegativeButton(R.string.generic_cancel, null)
            .setPositiveButton(R.string.annoy_unlock_dialog_action) { _, _ ->
                when (duration) {
                    UnlockDuration.Short -> logic.annoyLogic.doManualTempUnlock()
                    UnlockDuration.Long -> activity.showAuthenticationScreen()
                }.let {/* require handling all cases */}
            }
            .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)

    enum class UnlockDuration {
        Short, Long
    }
}