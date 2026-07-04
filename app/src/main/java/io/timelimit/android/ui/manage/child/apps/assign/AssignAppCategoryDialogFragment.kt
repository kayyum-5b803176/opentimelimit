/*
 * Open TimeLimit Copyright <C> 2019 - 2024 Jonas Lochmann
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
package io.timelimit.android.ui.manage.child.apps.assign

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.data.model.App
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryApp
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.BottomSheetSelectionListBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.RemoveCategoryAppsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder

class AssignAppCategoryDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val EXTRA_CHILD_ID = "childId"
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val TAG = "AssignAppCategoryDialogFragment"

        fun newInstance(childId: String, appPackageName: String) = AssignAppCategoryDialogFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_CHILD_ID, childId)
                putString(EXTRA_PACKAGE_NAME, appPackageName)
            }
        }
    }

    val childId: String by lazy { arguments!!.getString(EXTRA_CHILD_ID)!! }
    val appPackageName: String by lazy { arguments!!.getString(EXTRA_PACKAGE_NAME)!! }

    val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    val database: Database by lazy { logic.database }
    val auth: ActivityViewModel by lazy { (activity as ActivityViewModelHolder).getActivityViewModel() }

    val matchingAppEntries: LiveData<List<App>> by lazy {
        database.app().getAppsByPackageName(appPackageName)
    }

    val childCategoryEntries: LiveData<List<Category>> by lazy {
        database.category().getCategoriesByChildId(childId)
    }

    val categoryAppEntry: LiveData<CategoryApp?> by lazy {
        childCategoryEntries.switchMap { childCategories ->
            database.categoryApp().getCategoryApp(
                    categoryIds = childCategories.map { it.id },
                    packageName = appPackageName
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth.authenticatedUser.observe(this, Observer {
            if (it?.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = BottomSheetSelectionListBinding.inflate(inflater, container, false)

        val list = binding.list

        childCategoryEntries.switchMap { categories ->
            categoryAppEntry.map { appCategory ->
                categories to appCategory
            }
        }.observe(this, Observer { (categories, appCategory) ->
            list.removeAllViews()

            val hasCategory = appCategory != null && categories.find { it.id == appCategory.categoryId } != null

            fun buildRow(): CheckedTextView = LayoutInflater.from(context!!).inflate(
                    android.R.layout.simple_list_item_single_choice,
                    list,
                    false
            ) as CheckedTextView

            categories.forEach { category ->
                buildRow().let { row ->
                    row.text = category.title
                    row.isChecked = category.id == appCategory?.categoryId
                    row.setOnClickListener {
                        if (appCategory?.categoryId != category.id) {
                            auth.tryDispatchParentAction(
                                    AddCategoryAppsAction(
                                            categoryId = category.id,
                                            packageNames = listOf(appPackageName)
                                    )
                            )
                        }

                        dismiss()
                    }

                    list.addView(row)
                }
            }

            buildRow().let { row ->
                row.setText(R.string.child_apps_unassigned)
                row.isChecked = !hasCategory
                row.setOnClickListener {
                    if (appCategory != null) {
                        auth.tryDispatchParentAction(
                                RemoveCategoryAppsAction(
                                        categoryId = appCategory.categoryId,
                                        packageNames = listOf(appPackageName)
                                )
                        )
                    }

                    dismiss()
                }

                list.addView(row)
            }
        })

        matchingAppEntries.observe(this, Observer {
            binding.title = it.firstOrNull()?.title
        })

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, TAG)
}
