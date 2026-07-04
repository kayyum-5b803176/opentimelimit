/*
 * Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
package io.timelimit.android.ui.user.create

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.crypto.PasswordHashing
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.data.model.UserType
import io.timelimit.android.livedata.castDown
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.*
import io.timelimit.android.ui.main.ActivityViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AddUserModel(application: Application): AndroidViewModel(application) {
    private val logic: AppLogic by lazy { DefaultAppLogic.with(application) }

    private val statusInternal = MutableLiveData<AddUserModelStatus>().apply { value = AddUserModelStatus.Idle }
    private val createUserLock = Mutex()

    val status = statusInternal.castDown()

    fun tryCreateUser(name: String, password: String, type: UserType, model: ActivityViewModel) {
        runAsync {
            createUserLock.withLock {
                statusInternal.value = AddUserModelStatus.Working

                when (type) {
                    UserType.Parent -> {
                        if(
                                model.tryDispatchParentAction(
                                        AddUserAction(
                                                name = name,
                                                password = PasswordHashing.hashCoroutine(password),
                                                userType = UserType.Parent,
                                                userId = IdGenerator.generateId(),
                                                timeZone = logic.timeApi.getSystemTimeZone().id
                                        )
                                )
                        ) {
                            statusInternal.value = AddUserModelStatus.Done
                        } else {
                            statusInternal.value = AddUserModelStatus.Idle
                        }
                    }
                    UserType.Child -> {
                        val childId = IdGenerator.generateId()
                        val allowedAppsCategory = IdGenerator.generateId()
                        val allowedGamesCategory = IdGenerator.generateId()

                        // NOTE: the default config is created at the AddUserModel and at the AppSetupLogic
                        val defaultCategories = DefaultCategories.with(getApplication())

                        val actions = ArrayList<ParentAction>(listOf(
                                AddUserAction(
                                        name = name,
                                        password = if (password.isEmpty()) null else PasswordHashing.hashCoroutine(password),
                                        userType = UserType.Child,
                                        userId = childId,
                                        timeZone = logic.timeApi.getSystemTimeZone().id
                                ),
                                CreateCategoryAction(
                                        childId = childId,
                                        categoryId = allowedAppsCategory,
                                        title = defaultCategories.allowedAppsTitle
                                ),
                                CreateCategoryAction(
                                        childId = childId,
                                        categoryId = allowedGamesCategory,
                                        title = defaultCategories.allowedGamesTitle
                                )
                        ))

                        defaultCategories.generateGamesTimeLimitRules(allowedGamesCategory).forEach { rule ->
                            actions.add(CreateTimeLimitRuleAction(rule))
                        }

                        // add recommend allowed apps
                        val recommendAppWhitelist = logic.database.app()
                                .getAppsByRecommendationLive(AppRecommendation.Whitelist)
                                .waitForNonNullValue()
                                .asSequence()
                                .map { app -> app.packageName }
                                .distinct()
                                .toList()

                        if (recommendAppWhitelist.isNotEmpty()) {
                            actions.add(
                                    AddCategoryAppsAction(
                                            categoryId = allowedAppsCategory,
                                            packageNames = recommendAppWhitelist
                                    )
                            )
                        }

                        if(model.tryDispatchParentActions(actions)) {
                            statusInternal.value = AddUserModelStatus.Done
                        } else {
                            statusInternal.value = AddUserModelStatus.Idle
                        }
                    }
                }
            }
        }
    }
}

enum class AddUserModelStatus {
    Idle, Working, Done
}
