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
package io.timelimit.android.ui.manage.child.apps


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.data.model.App
import io.timelimit.android.databinding.ChildAppsFragmentBinding
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.child.apps.assign.AssignAppCategoryDialogFragment
import io.timelimit.android.ui.view.AppFilterView

class ChildAppsFragment : Fragment() {
    companion object {
        private const val CHILD_ID = "childId"

        fun newInstance(childId: String) = ChildAppsFragment().apply {
            arguments = Bundle().apply { putString(CHILD_ID, childId) }
        }
    }

    val childId: String get() = arguments!!.getString(CHILD_ID)!!
    val auth: ActivityViewModel by lazy { (activity as ActivityViewModelHolder).getActivityViewModel() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = ChildAppsFragmentBinding.inflate(inflater, container, false)
        val model = ViewModelProviders.of(this).get(ChildAppsModel::class.java)
        val adapter = ChildAppsAdapter()

        fun getMode() = when (binding.sortSetting.checkedRadioButtonId) {
            R.id.sort_by_category -> ChildAppsMode.SortByCategory
            R.id.sort_by_title -> ChildAppsMode.SortByTitle
            else -> throw IllegalArgumentException()
        }

        model.childIdLive.value = childId
        AppFilterView.getFilterLive(binding.appFilter).observe(viewLifecycleOwner) { model.appFilterLive.value = it }
        model.modeLive.value = getMode()
        binding.sortSetting.setOnCheckedChangeListener { _, _ -> model.modeLive.value = getMode() }

        model.listContentLive.observe(viewLifecycleOwner) { adapter.data = it }

        binding.recycler.layoutManager = LinearLayoutManager(context)
        binding.recycler.adapter = adapter

        adapter.handlers = object: Handlers {
            override fun onAppClicked(app: App) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    AssignAppCategoryDialogFragment.newInstance(
                            childId = childId,
                            appPackageName = app.packageName
                    ).show(parentFragmentManager)
                }
            }
        }

        return binding.root
    }
}
