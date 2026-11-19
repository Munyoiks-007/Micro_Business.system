package com.example.microbusinesssystem.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.microbusinesssystem.data.repository.AuthRepository
import com.example.microbusinesssystem.domain.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(email: String, pass: String, ownerName: String, businessName: String, phone: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authRepository.register(email, pass, ownerName, businessName, phone)) {
                is Resource.Success -> {
                    _uiState.value = AuthUiState.Success(result.data)
                }
                is Resource.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }
}
