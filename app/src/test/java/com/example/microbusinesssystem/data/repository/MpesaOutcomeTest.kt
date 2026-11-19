package com.example.microbusinesssystem.data.repository

import com.example.microbusinesssystem.data.local.PaymentStatus
import com.example.microbusinesssystem.data.remote.StkPushResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MpesaOutcomeTest {
    @Test
    fun darajaAcceptanceMeansPendingAndNeverCompleted() {
        val status = classifyStkResponse(
            isHttpSuccessful = true,
            httpCode = 200,
            body = StkPushResponse(
                MerchantRequestID = "merchant-1",
                CheckoutRequestID = "checkout-1",
                ResponseCode = "0"
            )
        )

        assertEquals(PaymentStatus.PENDING, status)
    }

    @Test
    fun confirmedRequestRejectionIsFailedButTimeoutIsUncertain() {
        assertEquals(
            PaymentStatus.FAILED,
            classifyStkResponse(true, 200, StkPushResponse(ResponseCode = "1"))
        )
        assertEquals(PaymentStatus.FAILED, classifyStkResponse(false, 400, null))
        assertEquals(PaymentStatus.UNCERTAIN, classifyStkResponse(false, 503, null))
    }

    @Test
    fun acceptedResponseMissingEitherReferenceIsUncertain() {
        assertEquals(
            PaymentStatus.UNCERTAIN,
            classifyStkResponse(
                true,
                200,
                StkPushResponse(ResponseCode = "0", CheckoutRequestID = "checkout-1")
            )
        )
        assertEquals(
            PaymentStatus.UNCERTAIN,
            classifyStkResponse(
                true,
                200,
                StkPushResponse(ResponseCode = "0", MerchantRequestID = "merchant-1")
            )
        )
    }

    @Test
    fun cancelledStkRequestCanPersistUncertainStateBeforeCancellationPropagates() = runBlocking {
        var localStatusWasPersisted = false
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                persistMpesaStateOnCancellation { localStatusWasPersisted = true }
                throw cancelled
            }
        }

        request.cancel()
        request.join()
        assertTrue(localStatusWasPersisted)
    }

    @Test
    fun statusPersistenceFailureDoesNotReplaceOriginalCancellation() = runBlocking {
        var originalCancellationPropagated = false
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                persistMpesaStateOnCancellation { error("local database unavailable") }
                originalCancellationPropagated = true
                throw cancelled
            }
        }

        request.cancel()
        request.join()
        assertTrue(originalCancellationPropagated)
    }
}
