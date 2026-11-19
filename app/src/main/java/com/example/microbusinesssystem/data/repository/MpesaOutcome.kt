
package com.example.microbusinesssystem.data.repository

import com.example.microbusinesssystem.data.local.PaymentStatus
import com.example.microbusinesssystem.data.remote.StkPushResponse
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Classifies the outcome of an STK Push initiation request.
 *
 * PENDING means Daraja accepted the request, not that the customer paid.
 */
fun classifyStkResponse(
    isHttpSuccessful: Boolean,
    httpCode: Int,
    body: StkPushResponse?
): PaymentStatus {
    // Daraja ResponseCode "0" means the STK request was accepted.
    if (isHttpSuccessful && body?.ResponseCode == "0") {
        return if (
            !body.CheckoutRequestID.isNullOrBlank() &&
            !body.MerchantRequestID.isNullOrBlank()
        ) {
            PaymentStatus.PENDING
        } else {
            PaymentStatus.UNCERTAIN
        }
    }

    // An explicit Daraja rejection is a failed initiation.
    if (isHttpSuccessful && body?.ResponseCode?.let { it.isNotBlank() && it != "0" } == true) {
        return PaymentStatus.FAILED
    }

    // Client-side HTTP errors generally indicate a rejected request.
    // Server errors can be ambiguous: Daraja may have processed the request.
    return when {
        !isHttpSuccessful && httpCode in 400..499 && httpCode != 408 -> PaymentStatus.FAILED
        else -> PaymentStatus.UNCERTAIN
    }
}

sealed interface MpesaInitiationResult {
    data object NotConfigured : MpesaInitiationResult
    data object AnotherAttemptUnresolved : MpesaInitiationResult
    data class Pending(val saleId: String, val cloudSynced: Boolean) : MpesaInitiationResult
    data class Uncertain(val saleId: String, val cloudSynced: Boolean) : MpesaInitiationResult
    data class Failed(
        val saleId: String,
        val cloudSynced: Boolean,
        val requestWasSent: Boolean,
        val failureMessage: String? = null
    ) : MpesaInitiationResult
    data class Error(val message: String) : MpesaInitiationResult
}

/** Makes a local payment-state write best-effort even after the caller's job is cancelled. */
internal suspend fun persistMpesaStateOnCancellation(persist: suspend () -> Unit) {
    withContext(NonCancellable) {
        runCatching { persist() }
    }
}
