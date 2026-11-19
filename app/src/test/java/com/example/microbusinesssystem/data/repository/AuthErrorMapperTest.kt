package com.example.microbusinesssystem.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun invalidCredentialsUseGenericUserFacingMessage() {
        assertEquals(
            "Incorrect email or password.",
            AuthErrorMapper.message("ERROR_INVALID_CREDENTIAL", networkError = false, registration = false)
        )
    }

    @Test
    fun invalidEmailAndNetworkErrorsAreActionable() {
        assertEquals(
            "Enter a valid email address.",
            AuthErrorMapper.message("ERROR_INVALID_EMAIL", networkError = false, registration = false)
        )
        assertEquals(
            "Check your internet connection and try again.",
            AuthErrorMapper.message(null, networkError = true, registration = false)
        )
    }

    @Test
    fun registrationDoesNotConfirmThatEmailIsAlreadyRegistered() {
        val message = AuthErrorMapper.message("ERROR_EMAIL_ALREADY_IN_USE", networkError = false, registration = true)
        assertEquals("Unable to create the account with these details. Please check them and try again.", message)
        org.junit.Assert.assertFalse(message.contains("already exists", ignoreCase = true))
    }
}
