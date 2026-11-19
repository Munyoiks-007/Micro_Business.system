package com.example.microbusinesssystem.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.microbusinesssystem.data.repository.SessionRepository
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.data.repository.UserProfile
import com.example.microbusinesssystem.databinding.ActivityRegisterBinding
import com.example.microbusinesssystem.ui.main.MainActivity
import com.example.microbusinesssystem.util.InputValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()

    @Inject
    lateinit var sessionRepository: SessionRepository

    private var mainActivityStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
        observeSession()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val ownerName = binding.etOwnerName.text.toString().trim()
            val businessName = binding.etBusinessName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (!InputValidator.isValidName(ownerName)) {
                binding.tilOwnerName.error = "Name cannot be empty"
                return@setOnClickListener
            } else {
                binding.tilOwnerName.error = null
            }

            if (!InputValidator.isValidName(businessName)) {
                binding.tilBusinessName.error = "Business name cannot be empty"
                return@setOnClickListener
            } else {
                binding.tilBusinessName.error = null
            }

            if (!InputValidator.isValidEmail(email)) {
                binding.tilEmail.error = "Please enter a valid email"
                return@setOnClickListener
            } else {
                binding.tilEmail.error = null
            }

            if (!InputValidator.isValidPassword(password)) {
                binding.tilPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            } else {
                binding.tilPassword.error = null
            }

            if (password != confirmPassword) {
                binding.tilConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            } else {
                binding.tilConfirmPassword.error = null
            }

            viewModel.register(email, password, ownerName, businessName, phone)
        }

        binding.tvLogin.setOnClickListener {
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Idle -> {
                            binding.btnRegister.isEnabled = true
                        }
                        is AuthUiState.Loading -> {
                            binding.btnRegister.isEnabled = false
                        }
                        is AuthUiState.Success<*> -> {
                            binding.btnRegister.isEnabled = true
                            val user = state.data as? UserProfile ?: return@collect
                            if (sessionRepository.acceptAuthenticatedProfile(user)) {
                                Toast.makeText(this@RegisterActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@RegisterActivity, "Your account was created, but your profile could not be loaded. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                        is AuthUiState.Error -> {
                            binding.btnRegister.isEnabled = true
                            Toast.makeText(this@RegisterActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun observeSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionRepository.sessionState.collect { state ->
                    when (state) {
                        is SessionState.Authenticated -> navigateToMain()
                        is SessionState.ProfileUnavailable -> Toast.makeText(
                            this@RegisterActivity, state.message, Toast.LENGTH_LONG
                        ).show()
                        SessionState.Loading, SessionState.Unauthenticated -> Unit
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        if (mainActivityStarted || isFinishing) return
        mainActivityStarted = true
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }
}
