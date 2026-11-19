package com.example.microbusinesssystem.data.session

import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.data.repository.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateControllerTest {

    @Test
    fun logoutAndAccountSwitchReplacePrivateProfileState() {
        val controller = SessionStateController()
        val profileA = profile("account-a", "A's business")
        val profileB = profile("account-b", "B's business")

        controller.authenticate(profileA)
        assertEquals(
            SessionState.Authenticated("account-a", "account-a@example.test", "Owner A", "A's business", "254700000001"),
            controller.sessionState.value
        )

        controller.begin(null)
        assertEquals(SessionState.Unauthenticated, controller.sessionState.value)

        controller.begin("account-b")
        assertEquals(SessionState.Loading, controller.sessionState.value)
        val accountBRequest = controller.begin("account-b")
        assertTrue(controller.complete(accountBRequest, profileB))
        assertEquals("account-b", (controller.sessionState.value as SessionState.Authenticated).uid)
        assertEquals("B's business", (controller.sessionState.value as SessionState.Authenticated).businessName)

        controller.authenticate(profileA)
        assertEquals("account-a", (controller.sessionState.value as SessionState.Authenticated).uid)
        assertEquals("A's business", (controller.sessionState.value as SessionState.Authenticated).businessName)
    }

    @Test
    fun staleProfileLoadCannotRestoreAnEarlierAccount() {
        val controller = SessionStateController()
        val profileA = profile("account-a", "A's business")
        val profileB = profile("account-b", "B's business")

        val accountARequest = controller.begin("account-a")
        val accountBRequest = controller.begin("account-b")

        assertFalse(controller.complete(accountARequest, profileA))
        assertEquals(SessionState.Loading, controller.sessionState.value)
        assertTrue(controller.complete(accountBRequest, profileB))
        assertEquals("account-b", (controller.sessionState.value as SessionState.Authenticated).uid)

        controller.begin(null)
        assertFalse(controller.complete(accountBRequest, profileB))
        assertEquals(SessionState.Unauthenticated, controller.sessionState.value)
    }

    @Test
    fun profileChangesReplaceSessionDataOnlyAfterSuccessfulUpdate() {
        val controller = SessionStateController()
        controller.authenticate(profile("account-a", "Old business"))

        assertTrue(controller.updateProfile("account-a", "Updated Owner", "Updated business", "254700000099"))
        assertEquals(
            SessionState.Authenticated(
                "account-a", "account-a@example.test", "Updated Owner", "Updated business", "254700000099"
            ),
            controller.sessionState.value
        )
        assertFalse(controller.updateProfile("account-b", "Other", "Other business", ""))
    }

    @Test
    fun unavailableProfileDoesNotBecomeAnAuthenticatedFallbackProfile() {
        val controller = SessionStateController()
        val request = controller.begin("account-a")

        assertTrue(controller.fail(request, "account-a", "Profile could not be loaded."))
        assertEquals(
            SessionState.ProfileUnavailable("account-a", "Profile could not be loaded."),
            controller.sessionState.value
        )
        assertFalse(controller.isAuthenticatedAs("account-a"))
    }

    private fun profile(uid: String, businessName: String) = UserProfile(
        uid = uid,
        email = "$uid@example.test",
        ownerName = "Owner ${uid.takeLast(1).uppercase()}",
        businessName = businessName,
        phone = "254700000001"
    )
}
