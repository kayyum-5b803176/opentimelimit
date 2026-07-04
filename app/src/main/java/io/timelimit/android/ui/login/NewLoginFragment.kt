/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
 * Copyright <C> 2020 Marcel Voigt
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
package io.timelimit.android.ui.login

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.User
import io.timelimit.android.databinding.NewLoginFragmentBinding
import io.timelimit.android.extensions.setOnEnterListenr
import io.timelimit.android.ui.extensions.openNextWizardScreen
import io.timelimit.android.ui.extensions.openPreviousWizardScreen
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.parent.key.MissingBarcodeScannerDialogFragment
import io.timelimit.android.ui.manage.parent.key.ScanBarcode
import io.timelimit.android.ui.manage.parent.key.ScannedKey
import io.timelimit.android.ui.view.KeyboardViewListener

class NewLoginFragment: DialogFragment() {
    companion object {
        const val SHOW_ON_LOCKSCREEN = "showOnLockscreen"

        fun newInstance(showOnLockscreen: Boolean) = NewLoginFragment().apply {
            arguments = Bundle().apply {
                putBoolean(SHOW_ON_LOCKSCREEN, showOnLockscreen)
            }
        }

        private const val SELECTED_USER_ID = "selectedUserId"
        private const val USER_LIST = 0
        private const val PARENT_AUTH = 1
        private const val CHILD_MISSING_PASSWORD = 2
        private const val CHILD_ALREADY_CURRENT_USER = 3
        private const val CHILD_AUTH = 4
        private const val PARENT_LOGIN_BLOCKED = 5
    }

    private val model: LoginDialogFragmentModel by viewModels()

    private val inputMethodManager: InputMethodManager by lazy {
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private val scanLoginCode = registerForActivityResult(ScanBarcode()) { barcode ->
        barcode ?: return@registerForActivityResult

        ScannedKey.tryDecode(barcode).let { key ->
            if (key == null) Toast.makeText(requireContext(), R.string.manage_user_key_invalid, Toast.LENGTH_SHORT).show()
            else tryCodeLogin(key)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState?.containsKey(SELECTED_USER_ID) == true) {
            model.selectedUserId.value = savedInstanceState.getString(SELECTED_USER_ID)
        }

        if (savedInstanceState == null) {
            model.tryDefaultLogin(getActivityViewModel(requireActivity()))
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = object: BottomSheetDialog(requireContext(), theme) {
        init {
            onBackPressedDispatcher.addCallback(object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!model.goBack()) dismissAllowingStateLoss()
                }
            })
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                if (arguments?.getBoolean(SHOW_ON_LOCKSCREEN, false) == true) {
                    window!!.addFlags(
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        model.selectedUserId.value?.let { outState.putString(SELECTED_USER_ID, it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = NewLoginFragmentBinding.inflate(inflater, container, false)

        val adapter = LoginUserAdapter()

        adapter.listener = object: LoginUserAdapterListener {
            override fun onUserClicked(user: User) {
                // reset parent password view
                binding.enterPassword.password.setText("")

                // go to the next step
                model.startSignIn(user)
            }

            override fun onScanCodeRequested() {
                try {
                    scanLoginCode.launch(null)
                } catch (ex: ActivityNotFoundException) {
                    MissingBarcodeScannerDialogFragment.newInstance().show(parentFragmentManager)
                }
            }
        }

        binding.userList.recycler.adapter = adapter
        binding.userList.recycler.layoutManager = LinearLayoutManager(context)

        binding.enterPassword.apply {
            showKeyboardButton.setOnClickListener {
                showCustomKeyboard = !showCustomKeyboard

                if (showCustomKeyboard) {
                    inputMethodManager.hideSoftInputFromWindow(password.windowToken, 0)
                } else {
                    inputMethodManager.showSoftInput(password, 0)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    password.showSoftInputOnFocus = !showCustomKeyboard
                }
            }

            fun go() {
                model.tryParentLogin(
                        password = password.text.toString(),
                        model = getActivityViewModel(requireActivity())
                )
            }

            keyboard.listener = object: KeyboardViewListener {
                override fun onItemClicked(content: String) {
                    val start = Math.max(password.selectionStart, 0)
                    val end = Math.max(password.selectionEnd, 0)

                    password.text.replace(Math.min(start, end), Math.max(start, end), content, 0, content.length)
                }

                override fun onGoClicked() {
                    go()
                }
            }

            password.setOnEnterListenr { go() }

            biometricAuthButton.setOnClickListener {
                tryBiometricLogin()
            }
        }

        binding.childPassword.apply {
            password.setOnEnterListenr {
                model.tryChildLogin(
                        password = password.text.toString()
                )
            }
        }

        model.status.observe(viewLifecycleOwner, Observer { status ->
            when (status) {
                LoginDialogDone -> {
                    dismissAllowingStateLoss()
                }
                is UserListLoginDialogStatus -> {
                    binding.switcher.openPreviousWizardScreen(USER_LIST)

                    val users = status.usersToShow.map { LoginUserAdapterUser(it) }

                    adapter.data =  if (status.showScanOption)
                        users + LoginUserAdapterScan
                    else
                        users

                    Threads.mainThreadHandler.post { binding.userList.recycler.requestFocus() }

                    null
                }
                is ParentUserLogin -> {
                    if (binding.switcher.displayedChild != PARENT_AUTH) {
                        binding.switcher.openNextWizardScreen(PARENT_AUTH)

                        if (status.biometricAuthEnabled && !model.biometricPromptDismissed) {
                            tryBiometricLogin()
                        }
                    }

                    binding.enterPassword.password.isEnabled = !status.isCheckingPassword
                    binding.enterPassword.biometricAuthEnabled = status.biometricAuthEnabled

                    if (!binding.enterPassword.showCustomKeyboard) {
                        binding.enterPassword.password.requestFocus()
                        inputMethodManager.showSoftInput(binding.enterPassword.password, 0)
                    }

                    if (status.wasPasswordWrong) {
                        Toast.makeText(requireContext(), R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                        binding.enterPassword.password.setText("")

                        model.resetPasswordWrong()
                    }

                    null
                }
                is CanNotSignInChildHasNoPassword -> {
                    binding.switcher.openNextWizardScreen(CHILD_MISSING_PASSWORD)

                    binding.childWithoutPassword.childName = status.childName

                    null
                }
                is ChildAlreadyDeviceUser -> {
                    binding.switcher.openNextWizardScreen(CHILD_ALREADY_CURRENT_USER)

                    null
                }
                is ChildUserLogin -> {
                    binding.switcher.openNextWizardScreen(CHILD_AUTH)

                    binding.childPassword.password.requestFocus()
                    inputMethodManager.showSoftInput(binding.childPassword.password, 0)

                    binding.childPassword.password.isEnabled = !status.isCheckingPassword

                    if (status.wasPasswordWrong) {
                        Toast.makeText(requireContext(), R.string.login_snackbar_wrong, Toast.LENGTH_SHORT).show()
                        binding.childPassword.password.setText("")

                        model.resetPasswordWrong()
                    }

                    null
                }
                is ParentUserLoginBlockedByCategory -> {
                    binding.switcher.openNextWizardScreen(PARENT_LOGIN_BLOCKED)

                    binding.parentLoginBlocked.categoryTitle = status.categoryTitle
                    binding.parentLoginBlocked.reason = LoginDialogFragmentModel.formatBlockingReasonForLimitLoginCategory(status.reason, requireContext())

                    null
                }
            }.let { /* require handling all cases */ }
        })

        return binding.root
    }

    fun tryCodeLogin(code: ScannedKey) {
        model.tryCodeLogin(code, getActivityViewModel(requireActivity()))
    }

    private fun tryBiometricLogin() {
        model.biometricPromptDismissed = false
        model.status.value?.let { status ->
            if (status is ParentUserLogin) {
                BiometricPrompt(this, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        model.biometricPromptDismissed = true
                        Toast.makeText(
                            context,
                            getString(R.string.biometric_auth_failed, status.userName) + "\n" +
                                    getString(R.string.biometric_auth_failed_reason, errString),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        model.biometricPromptDismissed = true
                        model.performBiometricLogin(getActivityViewModel(requireActivity()))
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        model.biometricPromptDismissed = true
                        Toast.makeText(context, getString(R.string.biometric_auth_failed, status.userName), Toast.LENGTH_LONG)
                            .show()
                    }
                }).authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.biometric_login_prompt_title))
                        .setSubtitle(status.userName)
                        .setDescription(getString(R.string.biometric_login_prompt_description, status.userName))
                        .setNegativeButtonText(getString(R.string.generic_cancel))
                        .setConfirmationRequired(false)
                        .build()
                )
            }
        }
    }

}
