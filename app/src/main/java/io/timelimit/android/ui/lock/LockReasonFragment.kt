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

package io.timelimit.android.ui.lock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.timelimit.android.databinding.LockReasonFragmentBinding
import io.timelimit.android.logic.BlockingLevel

class LockReasonFragment: Fragment() {
    val model: LockModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = LockReasonFragmentBinding.inflate(inflater, container, false)

        model.content.observe(viewLifecycleOwner) { content ->
            if (content is LockscreenContent.Blocked) {
                binding.activityName = if (content.enableActivityLevelBlocking) content.appActivityName?.removePrefix(content.appPackageName) else null
                binding.reason = content.reason
                binding.blockedKindLabel = when (content.level) {
                    BlockingLevel.Activity -> "Activity"
                    BlockingLevel.App -> "App"
                }
                binding.appCategoryTitle = if (content is LockscreenContent.Blocked.BlockedCategory) content.appCategoryTitle else null
            }
        }

        model.packageAndActivityNameLive.observe(viewLifecycleOwner) { binding.packageName = it.first }
        binding.appIcon.setImageDrawable(model.icon)
        binding.appTitle = model.title

        return binding.root
    }
}