package com.example.microbusinesssystem.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

class StkPushRequestFactoryTest {
    private val configuration = DarajaConfiguration(
        consumerKey = "sandbox-key",
        consumerSecret = "sandbox-secret",
        shortCode = "174379",
        passKey = "sandbox-pass-key",
        callbackUrl = "https://example.test/callback"
    )

    @Test
    fun createsContractFieldsWithNormalizedPhoneAndMinorUnits() {
        val now = LocalDateTime.of(2024, 2, 29, 12, 34, 56)
            .atZone(ZoneId.of("Africa/Nairobi")).toInstant().toEpochMilli()
        val request = StkPushRequestFactory.create(configuration, 1_250_000L, "+254 712-345-678", "Tea order", now)

        assertEquals("174379", request.BusinessShortCode)
        assertEquals("CustomerPayBillOnline", request.TransactionType)
        assertEquals(12_500L, request.Amount)
        assertEquals("254712345678", request.PartyA)
        assertEquals("254712345678", request.PhoneNumber)
        assertEquals("174379", request.PartyB)
        assertEquals("20240229123456", request.Timestamp)
        val decodedPassword = String(Base64.getDecoder().decode(request.Password), Charsets.UTF_8)
        assertEquals("174379sandbox-pass-key20240229123456", decodedPassword)
        assertEquals("https://example.test/callback", request.CallBackURL)
        assertTrue(request.TransactionDesc.length <= 20)
    }

    @Test
    fun acceptsCommonKenyanPhoneFormatsAndRejectsInvalidNumbers() {
        assertEquals("254712345678", KenyanPhoneNumber.normalize("0712345678"))
        assertEquals("254112345678", KenyanPhoneNumber.normalize("+254 112 345 678"))
        assertEquals(null, KenyanPhoneNumber.normalize("1234"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFractionalShillingAmount() {
        StkPushRequestFactory.create(configuration, 1_025L, "0712345678", "Sale", 0L)
    }
}
