package com.example.microbusinesssystem.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class DarajaTokenProviderTest {
    @Test
    fun reusesTokenUntilSafetyWindowThenRequestsAnother() = runBlocking {
        var calls = 0
        val api = object : DarajaApiService {
            override suspend fun getAccessToken(authorization: String): Response<DarajaTokenResponse> {
                calls++
                assert(authorization.startsWith("Basic "))
                return Response.success(DarajaTokenResponse("token-$calls", "3600"))
            }

            override suspend fun sendStkPush(authorization: String, request: StkPushRequest) =
                error("STK request is not expected in token caching test")
        }
        val config = DarajaConfiguration("key", "secret", "174379", "pass", "https://example.test/callback")
        val provider = DarajaTokenProvider(api, config)

        assertEquals("token-1", provider.accessToken(nowMillis = 1_000L))
        assertEquals("token-1", provider.accessToken(nowMillis = 2_000L))
        assertEquals(1, calls)
        assertEquals("token-2", provider.accessToken(nowMillis = 3_600_000L))
        assertEquals(2, calls)
    }
}
