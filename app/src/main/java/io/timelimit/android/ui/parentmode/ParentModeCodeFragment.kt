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
package io.timelimit.android.ui.parentmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.timelimit.android.barcode.BarcodeMaskDrawable
import io.timelimit.android.databinding.ParentModeCodeFragmentBinding
import io.timelimit.android.logic.DefaultAppLogic

class ParentModeCodeFragment: Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ParentModeCodeFragmentBinding.inflate(inflater, container, false)
        val model = ViewModelProvider(this).get(ParentModeCodeModel::class.java)

        model.init(DefaultAppLogic.with(context!!).database)

        model.barcodeContent.observe(viewLifecycleOwner, Observer {
            binding.image.setImageDrawable(if (it != null) BarcodeMaskDrawable(it) else null)
        })

        model.keyId.observe(viewLifecycleOwner, Observer {
            binding.keyId = it
        })

        return binding.root
    }
}