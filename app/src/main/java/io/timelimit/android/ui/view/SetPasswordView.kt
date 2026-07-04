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
package io.timelimit.android.ui.view

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import io.timelimit.android.databinding.ViewSetPasswordBinding
import io.timelimit.android.livedata.and
import io.timelimit.android.livedata.or
import io.timelimit.android.util.PasswordValidator

class SetPasswordView(context: Context, attributeSet: AttributeSet): FrameLayout(context, attributeSet) {
    private val binding = ViewSetPasswordBinding.inflate(
            LayoutInflater.from(context),
            this,
            true
    )

    val password = MutableLiveData<String>()
    val passwordRepeat = MutableLiveData<String>()
    val allowNoPassword = MutableLiveData<Boolean>().apply { value = false }
    val noPasswordChecked = MutableLiveData<Boolean>().apply { value = false }
    val useEmptyPassword = allowNoPassword.and(noPasswordChecked)

    fun readPassword() = if (useEmptyPassword.value!!)
        ""
    else
        password.value!!

    init {
        password.value = ""
        passwordRepeat.value = ""

        binding.fieldPassword.addTextChangedListener(object: TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                password.value = binding.fieldPassword.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
                // ignore
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // ignore
            }
        })

        binding.fieldPasswordRepeat.addTextChangedListener(object: TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                passwordRepeat.value = binding.fieldPasswordRepeat.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
                // ignore
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // ignore
            }
        })

        password.observeForever { binding.password = it!! }
        passwordRepeat.observeForever() { binding.passwordRepeat = it!! }
        allowNoPassword.observeForever { binding.allowNoPassword = it }
        noPasswordChecked.observeForever {
            binding.noPasswordChecked = it

            if (binding.noPasswordCheckbox.isChecked != it) {
                binding.noPasswordCheckbox.isChecked = it
            }
        }

        binding.noPasswordCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != noPasswordChecked.value) {
                noPasswordChecked.value = isChecked
            }
        }
    }

    private val passwordQualityProblem: LiveData<String?> = password.map {
        if (it.isEmpty()) {
            null
        } else {
            PasswordValidator.validate(it, context)
        }
    }
    private val passwordsNotEqualProblem: LiveData<Boolean> = password.switchMap {
        val passwordValue = it

        passwordRepeat.map {
            (passwordValue.isNotEmpty() && it.isNotEmpty()) && passwordValue != it
        }
    }
    val passwordOk: LiveData<Boolean> = useEmptyPassword.or(password.switchMap {
        val password1 = it

        passwordRepeat.map {
            val password2 = it

            password1.isNotEmpty() && password2.isNotEmpty() && (password1 == password2) &&
                    (PasswordValidator.validate(password1, context) == null)
        }
    })

    init {
        passwordQualityProblem .observeForever { binding.passwordProblem = it }
        passwordsNotEqualProblem.observeForever { binding.passwordsNotEqualProblem = it }
        passwordOk.observeForever { /* keep the value fresh */ }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        binding.noPasswordCheckbox.isEnabled = enabled
        binding.fieldPassword.isEnabled = enabled
        binding.fieldPasswordRepeat.isEnabled = enabled
    }
}
