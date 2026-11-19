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
import com.example.microbusinesssystem.databinding.ActivityLoginBinding
import com.example.microbusinesssystem.ui.main.MainActivity
import com.example.microbusinesssystem.util.InputValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var sessionRepository: SessionRepository

    private var mainActivityStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
        observeSession()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (!InputValidator.isValidEmail(email)) {
                binding.tilEmail.error = "Enter a valid email address."
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

            viewModel.login(email, password)
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Idle -> {
                            binding.btnLogin.isEnabled = true
                        }
                        is AuthUiState.Loading -> {
                            binding.btnLogin.isEnabled = false
                        }
                        is AuthUiState.Success<*> -> {
                            binding.btnLogin.isEnabled = true
                            val user = state.data as? UserProfile ?: return@collect
                            if (sessionRepository.acceptAuthenticatedProfile(user)) {
                                Toast.makeText(this@LoginActivity, "Welcome back, ${user.ownerName}!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@LoginActivity, "Your session could not be confirmed. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                        is AuthUiState.Error -> {
                            binding.btnLogin.isEnabled = true
                            Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
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
                            this@LoginActivity, state.message, Toast.LENGTH_LONG
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
        finish()
    }
}
