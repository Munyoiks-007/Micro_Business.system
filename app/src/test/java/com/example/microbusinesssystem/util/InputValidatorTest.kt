package com.example.microbusinesssystem.util

import org.junit.Assert.*
import org.junit.Test

class InputValidatorTest {

    @Test
    fun testEmailValidation() {
        assertTrue(InputValidator.isValidEmail("owner@business.com"))
        assertFalse(InputValidator.isValidEmail("invalid-email"))
        assertFalse(InputValidator.isValidEmail(""))
    }

    @Test
    fun testPasswordValidation() {
        assertTrue(InputValidator.isValidPassword("secret123"))
        assertFalse(InputValidator.isValidPassword("123"))
    }

    @Test
    fun testNameValidation() {
        assertTrue(InputValidator.isValidName("John Doe"))
        assertFalse(InputValidator.isValidName("   "))
    }

    @Test
    fun testPhoneValidation() {
        assertTrue(InputValidator.isValidPhone("254712345678"))
        assertTrue(InputValidator.isValidPhone(""))
        assertFalse(InputValidator.isValidPhone("0712345678"))
    }

    @Test
    fun testPriceAndQuantityValidation() {
        assertTrue(InputValidator.isValidPrice(150.0))
        assertFalse(InputValidator.isValidPrice(0.0))
        assertFalse(InputValidator.isValidPrice(-10.0))

        assertTrue(InputValidator.isValidQuantity(10))
        assertFalse(InputValidator.isValidQuantity(0))
        assertFalse(InputValidator.isValidQuantity(-5))
    }
}
