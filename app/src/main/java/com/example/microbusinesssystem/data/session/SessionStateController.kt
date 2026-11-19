package com.example.microbusinesssystem.data.session

import com.example.microbusinesssystem.data.repository.SessionState
import com.example.microbusinesssystem.data.repository.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionStateController {
    private val mutableSessionState = MutableStateFlow<SessionState>(SessionState.Loading)
    val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()

    private var generation = 0L
    private var pendingUid: String? = null

    @Synchronized
    fun begin(uid: String?): Long {
        generation += 1
        pendingUid = uid
        mutableSessionState.value = if (uid == null) {
            SessionState.Unauthenticated
        } else {
            SessionState.Loading
        }
        return generation
    }

    @Synchronized
    fun complete(requestId: Long, profile: UserProfile): Boolean {
        if (requestId != generation || pendingUid != profile.uid) return false
        mutableSessionState.value = profile.toSessionState()
        return true
    }

    @Synchronized
    fun fail(requestId: Long, uid: String, message: String): Boolean {
        if (requestId != generation || pendingUid != uid) return false
        mutableSessionState.value = SessionState.ProfileUnavailable(uid, message)
        return true
    }

    @Synchronized
    fun authenticate(profile: UserProfile) {
        generation += 1
        pendingUid = profile.uid
        mutableSessionState.value = profile.toSessionState()
    }

    @Synchronized
    fun updateProfile(uid: String, ownerName: String, businessName: String, phone: String): Boolean {
        val current = mutableSessionState.value as? SessionState.Authenticated ?: return false
        if (pendingUid != uid || current.uid != uid) return false
        mutableSessionState.value = current.copy(
            ownerName = ownerName,
            businessName = businessName,
            phone = phone
        )
        return true
    }

    @Synchronized
    fun isAuthenticatedAs(uid: String): Boolean {
        val current = mutableSessionState.value as? SessionState.Authenticated ?: return false
        return pendingUid == uid && current.uid == uid
    }

    private fun UserProfile.toSessionState() = SessionState.Authenticated(
        uid = uid,
        email = email,
        ownerName = ownerName,
        businessName = businessName,
        phone = phone
    )
}
