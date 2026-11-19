package com.example.microbusinesssystem.ui.auth

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success<T>(val data: T) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
