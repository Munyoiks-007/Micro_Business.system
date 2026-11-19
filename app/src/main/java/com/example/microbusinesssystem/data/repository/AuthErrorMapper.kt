package com.example.microbusinesssystem.data.repository

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseNetworkException

internal object AuthErrorMapper {
    fun message(error: Exception, registration: Boolean): String {
        return message(
            code = (error as? FirebaseAuthException)?.errorCode,
            networkError = error is FirebaseNetworkException,
            registration = registration
        )
    }

    fun message(code: String?, networkError: Boolean, registration: Boolean): String {
        if (networkError) {
            return "Check your internet connection and try again."
        }

        if (code == "ERROR_NETWORK_REQUEST_FAILED") {
            return "Check your internet connection and try again."
        }

        return when (code.orEmpty()) {
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND",
            "INVALID_LOGIN_CREDENTIALS", "ERROR_INVALID_LOGIN_CREDENTIALS", "invalid-credential" ->
                "Incorrect email or password."
            "ERROR_WEAK_PASSWORD" -> "Choose a password with at least 6 characters."
            // Keep registration errors generic to avoid confirming whether an email is registered.
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Unable to create the account with these details. Please check them and try again."
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_CONFIGURATION_NOT_FOUND" ->
                "Sign-in is temporarily unavailable. Please try again later."
            else -> if (registration) {
                "Unable to create your account. Please check your details and try again."
            } else {
                "Unable to sign in. Please try again."
            }
        }
    }
}
