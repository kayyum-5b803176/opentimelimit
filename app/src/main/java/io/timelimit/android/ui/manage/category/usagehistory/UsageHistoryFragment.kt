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
package io.timelimit.android.ui.manage.category.usagehistory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentUsageHistoryBinding

class UsageHistoryFragment : Fragment(), SelectUsageHistoryCategoryDialog.Listener {
    companion object {
        private const val USER_ID = "userId"
        private const val CATEGORY_ID = "categoryId"

        fun newInstance(userId: String, categoryId: String?) = UsageHistoryFragment().apply {
            arguments = Bundle().apply {
                putString(USER_ID, userId)
                if (categoryId != null) putString(CATEGORY_ID, categoryId)
            }
        }
    }

    private val model: UsageHistoryModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentUsageHistoryBinding.inflate(inflater, container, false)
        val adapter = UsageHistoryAdapter()

        if (requireArguments().getString(CATEGORY_ID) != null) {
            binding.selectCategoryButton.visibility = View.GONE
        }

        if (!model.didInit) {
            model.userId.value = requireArguments().getString(USER_ID)!!
            model.categoryId.value = requireArguments().getString(CATEGORY_ID)

            model.didInit = true
        }

        model.categoryId.observe(viewLifecycleOwner) { adapter.showCategoryTitle = it == null }
        model.selectedCategoryName.observe(viewLifecycleOwner) { binding.selectCategoryButton.text = it ?: getString(R.string.usage_history_filter_all_categories) }
        model.listContent.observe(viewLifecycleOwner) {
            binding.isEmpty = it.isEmpty()
            adapter.submitList(it)
        }

        binding.recycler.adapter = adapter
        binding.recycler.layoutManager = LinearLayoutManager(context)

        binding.selectCategoryButton.setOnClickListener {
            SelectUsageHistoryCategoryDialog.newInstance(
                    userId = model.userId.value!!,
                    currentCategoryId = model.categoryId.value,
                    target = this
            ).show(parentFragmentManager)
        }

        return binding.root
    }

    override fun onAllCategoriesSelected() { model.categoryId.value = null }
    override fun onCategoryFilterSelected(categoryId: String) { model.categoryId.value = categoryId }
}
