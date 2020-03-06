/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.example.biometricloginsample

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.Observer
import com.example.biometricloginsample.databinding.ActivityEnableBiometricLoginBinding

class EnableBiometricLoginActivity : AppCompatActivity() {
    private val TAG = "EnableBiometricLogin"
    private lateinit var cryptographyManager: CryptographyManager
    private val loginViewModel by viewModels<LoginWithPasswordViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityEnableBiometricLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cancel.setOnClickListener { finish() }

        loginViewModel.loginWithPasswordFormState.observe(this, Observer {
            val loginState = it ?: return@Observer
            when (loginState) {
                is SuccessfulLoginFormState -> binding.authorize.isEnabled = loginState.isDataValid
                is FailedLoginFormState -> {
                    loginState.usernameError?.let { binding.username.error = getString(it) }
                    loginState.passwordError?.let { binding.password.error = getString(it) }
                }
            }
        })

        binding.username.afterTextChanged {
            loginViewModel.onLoginDataChanged(
                binding.username.text.toString(),
                binding.password.text.toString()
            )
        }

        binding.password.apply {
            afterTextChanged {
                loginViewModel.onLoginDataChanged(
                    binding.username.text.toString(),
                    binding.password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginWithPassword(
                            binding.username.text.toString(),
                            binding.password.text.toString()
                        )
                }
                false
            }
        }
        binding.authorize.setOnClickListener {
            loginWithPassword(binding.username.text.toString(), binding.password.text.toString())
        }
    }

    private fun loginWithPassword(username: String, password: String) {
        val succeeded = loginViewModel.login(username, password)
        if (succeeded) {
            // you need to save the userToken you got from the server. That way,
            // next time the user authenticates with biometrics, you will have the token for
            // server calls. Use BiometricPrompt.CryptoObject to guard access to the token,
            // that way, the user really must authenticate to get access to the server userToken.

            showBiometricPromptForEncryption()
        }
    }

    private fun showBiometricPromptForEncryption() {
        val canAuthenticate = BiometricManager.from(applicationContext).canAuthenticate()
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val secretKeyName = getString(R.string.secret_key_name)
            cryptographyManager = CryptographyManager()
            val cipher = cryptographyManager.getInitializedCipherForEncryption(secretKeyName)
            val biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(this, ::encryptAndStoreServerToken)
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun encryptAndStoreServerToken(authResult: BiometricPrompt.AuthenticationResult) {
        authResult.cryptoObject?.cipher?.apply {
            SampleAppUser.fakeToken?.let { token ->
                Log.d(TAG, "The token from server is $token")
                val encryptedServerTokenWrapper = cryptographyManager.encryptData(token, this)
                cryptographyManager.persistCiphertextWrapperToSharedPrefs(
                    encryptedServerTokenWrapper,
                    applicationContext,
                    SHARED_PREFS_FILENAME,
                    Context.MODE_PRIVATE,
                    CIPHERTEXT_WRAPPER
                )
            }
        }
        finish()
    }
}
