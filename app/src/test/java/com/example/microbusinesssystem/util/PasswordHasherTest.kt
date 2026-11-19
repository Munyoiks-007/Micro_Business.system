package com.example.microbusinesssystem.util

import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun testPasswordHashingAndVerification() {
        val password = "securePassword123"
        val hashed = PasswordHasher.hash(password)

        assertNotNull(hashed)
        assertNotEquals(password, hashed)
        assertTrue(PasswordHasher.verify(password, hashed))
        assertFalse(PasswordHasher.verify("wrongPassword", hashed))
    }
}
