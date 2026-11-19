package com.example.microbusinesssystem.util

object InputValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    private val PHONE_REGEX = Regex("^2547\\d{8}$")

    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && EMAIL_REGEX.matches(email)
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidName(name: String): Boolean {
        return name.isNotBlank()
    }

    fun isValidPhone(phone: String): Boolean {
        return phone.isBlank() || PHONE_REGEX.matches(phone)
    }

    fun isValidPrice(price: Double): Boolean {
        return price > 0.0
    }

    fun isValidQuantity(qty: Int): Boolean {
        return qty > 0
    }
}
