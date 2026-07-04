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
package io.timelimit.android.ui.manage.category.settings.networks

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryFlags
import io.timelimit.android.data.model.CategoryNetworkId
import io.timelimit.android.databinding.ManageCategoryNetworksViewBinding
import io.timelimit.android.integration.platform.NetworkId
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.sync.actions.AddCategoryNetworkId
import io.timelimit.android.sync.actions.ResetCategoryNetworkIds
import io.timelimit.android.sync.actions.UpdateCategoryFlagsAction
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel

object ManageCategoryNetworksView {
    fun bind(
            view: ManageCategoryNetworksViewBinding,
            auth: ActivityViewModel,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager,
            categoryId: String,
            fragment: Fragment,
            permissionRequestCode: Int,
            categoryLive: LiveData<Category?>
    ) {
        fun networkId(): NetworkId = auth.logic.platformIntegration.getCurrentNetworkId()

        val context = view.root.context
        val networkIdLive = liveDataFromFunction { networkId() }
        val networksLive = auth.logic.database.categoryNetworkId().getByCategoryIdLive(categoryId)

        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.category_networks_title,
                    text = R.string.category_networks_help
            ).show(fragmentManager)
        }

        networksLive.switchMap { networks ->
            networkIdLive.map { networkId ->
                networks to networkId
            }
        }.observe(lifecycleOwner, Observer { (networks, networkId) ->
            view.showRemoveNetworksButton = networks.isNotEmpty()

            view.addedNetworksText = if (networks.isEmpty())
                context.getString(R.string.category_networks_empty)
            else
                context.resources.getQuantityString(R.plurals.category_networks_not_empty, networks.size, networks.size)

            view.status = when (networkId) {
                NetworkId.MissingPermission -> NetworkStatus.MissingPermission
                NetworkId.NoNetworkConnected -> NetworkStatus.NoneConnected
                is NetworkId.Network -> {
                    val hasItem = networks.find {item ->
                        CategoryNetworkId.anonymizeNetworkId(networkId = networkId.id, itemId = item.networkItemId) == item.hashedNetworkId
                    } != null

                    if (hasItem)
                        NetworkStatus.ConnectedAndAdded
                    else if (networks.size + 1 > CategoryNetworkId.MAX_ITEMS)
                        NetworkStatus.ConnectedNotAddedButFull
                    else
                        NetworkStatus.ConnectedButNotAdded
                }
            }
        })

        view.removeBtn.setOnClickListener {
            val oldList = networksLive.value ?: return@setOnClickListener

            if (
                    auth.tryDispatchParentAction(
                            ResetCategoryNetworkIds(categoryId = categoryId)
                    )
            ) {
                Snackbar.make(view.root, R.string.category_networks_toast_all_removed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.generic_undo) {
                            val isEmpty = networksLive.value?.isEmpty() ?: false

                            if (isEmpty) {
                                auth.tryDispatchParentActions(
                                        oldList.map { item ->
                                            AddCategoryNetworkId(
                                                    categoryId = item.categoryId,
                                                    itemId = item.networkItemId,
                                                    hashedNetworkId = item.hashedNetworkId
                                            )
                                        }
                                )
                            }
                        }.show()
            }
        }

        view.grantPermissionButton.setOnClickListener {
            RequestWifiPermission.doRequest(fragment, permissionRequestCode, auth.logic.platformIntegration)
        }

        view.addNetworkButton.setOnClickListener {
            val itemId = IdGenerator.generateId()
            val networkId = networkId()

            if (!(networkId is NetworkId.Network)) return@setOnClickListener

            auth.tryDispatchParentAction(
                    AddCategoryNetworkId(
                            categoryId = categoryId,
                            itemId = itemId,
                            hashedNetworkId = CategoryNetworkId.anonymizeNetworkId(itemId = itemId, networkId = networkId.id)
                    )
            )
        }

        categoryLive.observe(lifecycleOwner) { category ->
            view.networkModeGroup.setOnCheckedChangeListener(null)

            if (category != null) {
                view.networkModeGroup.check(
                        if (category.hasBlockedNetworkList) R.id.network_mode_block else R.id.network_mode_allow
                )

                view.networkModeGroup.setOnCheckedChangeListener { _, id ->
                    if (id == R.id.network_mode_block) {
                        if (!category.hasBlockedNetworkList) {
                            if (
                                    !auth.tryDispatchParentAction(
                                        UpdateCategoryFlagsAction(
                                                categoryId = categoryId,
                                                modifiedBits = CategoryFlags.HAS_BLOCKED_NETWROK_LIST,
                                                newValues = CategoryFlags.HAS_BLOCKED_NETWROK_LIST
                                        )
                                    )
                            ) {
                                view.networkModeGroup.check(R.id.network_mode_allow)
                            }
                        }
                    } else if (id == R.id.network_mode_allow) {
                        if (category.hasBlockedNetworkList) {
                            if (
                                !auth.tryDispatchParentAction(
                                        UpdateCategoryFlagsAction(
                                                categoryId = categoryId,
                                                modifiedBits = CategoryFlags.HAS_BLOCKED_NETWROK_LIST,
                                                newValues = 0
                                        )
                                )
                            ) {
                                view.networkModeGroup.check(R.id.network_mode_block)
                            }
                        }
                    }
                }
            }
        }
    }

    enum class NetworkStatus {
        MissingPermission,
        NoneConnected,
        ConnectedButNotAdded,
        ConnectedNotAddedButFull,
        ConnectedAndAdded
    }
}