package com.example.microbusinesssystem.data.remote

data class DarajaConfiguration(
    val consumerKey: String,
    val consumerSecret: String,
    val shortCode: String,
    val passKey: String,
    val callbackUrl: String
) {
    val isSandboxConfigured: Boolean
        get() = consumerKey.isNotBlank() &&
            consumerSecret.isNotBlank() &&
            shortCode.isNotBlank() &&
            passKey.isNotBlank() &&
            callbackUrl.let { value ->
                runCatching { java.net.URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() } == true
            }
}

object DarajaEnvironment {
    const val SANDBOX_BASE_URL = "https://sandbox.safaricom.co.ke/"
    const val PRODUCTION_BASE_URL = "https://api.safaricom.co.ke/"
}
