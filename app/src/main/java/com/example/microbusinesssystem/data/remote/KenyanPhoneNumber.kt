package com.example.microbusinesssystem.data.remote

object KenyanPhoneNumber {
    fun normalize(input: String): String? {
        val cleaned = input.trim().replace(Regex("[\\s()-]"), "")
        val digits = when {
            cleaned.startsWith("+254") -> cleaned.drop(1)
            cleaned.startsWith("254") -> cleaned
            cleaned.startsWith("0") && cleaned.length == 10 -> "254${cleaned.drop(1)}"
            cleaned.length == 9 && (cleaned.startsWith("7") || cleaned.startsWith("1")) -> "254$cleaned"
            else -> return null
        }
        return digits.takeIf { it.matches(Regex("254[17]\\d{8}")) }
    }
}
