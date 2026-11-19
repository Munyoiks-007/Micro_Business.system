package com.example.microbusinesssystem.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DarajaTokenProvider @Inject constructor(
    private val api: DarajaApiService,
    private val configuration: DarajaConfiguration
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtMillis: Long = 0L
    @Volatile
    var lastFailureHttpCode: Int? = null
        private set

    suspend fun accessToken(nowMillis: Long = System.currentTimeMillis()): String? = mutex.withLock {
        cachedToken?.takeIf { nowMillis < expiresAtMillis }?.let { return@withLock it }
        lastFailureHttpCode = null
        if (!configuration.isSandboxConfigured) return@withLock null

        val credentials = "${configuration.consumerKey}:${configuration.consumerSecret}"
        val basic = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
        val response: Response<DarajaTokenResponse> = api.getAccessToken("Basic $basic")
        if (!response.isSuccessful) {
            lastFailureHttpCode = response.code()
            return@withLock null
        }
        val body = response.body() ?: return@withLock null
        val token = body.access_token?.takeIf { it.isNotBlank() } ?: return@withLock null
        val lifetimeSeconds = body.expires_in?.toLongOrNull()?.takeIf { it > 0 } ?: return@withLock null
        val safetySeconds = minOf(60L, (lifetimeSeconds / 10L).coerceAtLeast(1L))
        cachedToken = token
        expiresAtMillis = nowMillis + (lifetimeSeconds - safetySeconds).coerceAtLeast(1L) * 1_000L
        token
    }
}
