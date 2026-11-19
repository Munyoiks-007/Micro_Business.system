package com.example.microbusinesssystem.domain

sealed class Result<out T> {
    data class Success<out T>(val value: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.localizedMessage ?: "Unknown error") : Result<Nothing>()
    object Loading : Result<Nothing>()
}
