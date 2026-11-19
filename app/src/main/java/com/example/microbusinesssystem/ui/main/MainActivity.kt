package com.example.microbusinesssystem.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.microbusinesssystem.R
import com.example.microbusinesssystem.data.repository.SessionRepository
import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.databinding.ActivityMainBinding
import com.example.microbusinesssystem.ui.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navigationConfigured = false
    private var loginActivityStarted = false

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navHostFragment.visibility = android.view.View.GONE
        binding.bottomNavigationView.visibility = android.view.View.GONE

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionRepository.sessionState.collect { state ->
                    when (state) {
                        SessionState.Loading -> {
                            binding.navHostFragment.visibility = android.view.View.GONE
                            binding.bottomNavigationView.visibility = android.view.View.GONE
                        }
                        SessionState.Unauthenticated -> navigateToLogin()
                        is SessionState.ProfileUnavailable -> navigateToLogin()
                        is SessionState.Authenticated -> showAuthenticatedNavigation()
                    }
                }
            }
        }
    }

    private fun showAuthenticatedNavigation() {
        if (!navigationConfigured) {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as NavHostFragment
            binding.bottomNavigationView.setupWithNavController(navHostFragment.navController)
            navigationConfigured = true
        }
        binding.navHostFragment.visibility = android.view.View.VISIBLE
        binding.bottomNavigationView.visibility = android.view.View.VISIBLE
    }

    private fun navigateToLogin() {
        if (loginActivityStarted || isFinishing) return
        loginActivityStarted = true
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
