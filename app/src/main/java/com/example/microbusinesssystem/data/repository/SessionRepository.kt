package com.example.microbusinesssystem.data.repository

import android.util.Log
import com.example.microbusinesssystem.data.session.SessionManager
import com.example.microbusinesssystem.data.session.SessionStateController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.microbusinesssystem.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    object Loading : SessionState
    data class Authenticated(
        val uid: String,
        val email: String,
        val ownerName: String,
        val businessName: String,
        val phone: String
    ) : SessionState
    data class ProfileUnavailable(val uid: String, val message: String) : SessionState
    object Unauthenticated : SessionState
}

@Singleton
class SessionRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    private val stateController = SessionStateController()
    private val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var profileLoadJob: Job? = null

    val sessionState: StateFlow<SessionState> = stateController.sessionState

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        val authenticatedState = sessionState.value as? SessionState.Authenticated
        if (user != null && authenticatedState?.uid == user.uid) {
            // Login/registration already loaded this profile. A late initial auth callback
            // must not replace a valid session with a second, potentially failing read.
            return@AuthStateListener
        }
        profileLoadJob?.cancel()
        val requestId = stateController.begin(user?.uid)

        if (user == null) {
            sessionManager.clearSession()
        } else {
            val uid = user.uid
            val email = user.email.orEmpty()
            profileLoadJob = profileScope.launch {
                try {
                    val profile = authRepository.loadProfile(uid, email)
                    if (firebaseAuth.currentUser?.uid == uid) {
                        stateController.complete(requestId, profile)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val code = (error as? FirebaseFirestoreException)?.code?.name ?: "unavailable"
                    Log.w(TAG, "Profile load failed (type=${error.javaClass.simpleName}, code=$code)")
                    if (firebaseAuth.currentUser?.uid == uid) {
                        stateController.fail(requestId, uid, profileError(error))
                    }
                }
            }
        }
    }

    init {
        if (sessionManager.requiresReauthentication(BuildConfig.APP_BUILD_ID)) {
            firebaseAuth.signOut()
        }
        sessionManager.clearSession()
        stateController.begin(firebaseAuth.currentUser?.uid)
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    fun acceptAuthenticatedProfile(profile: UserProfile): Boolean {
        val currentUser = firebaseAuth.currentUser ?: return false
        if (currentUser.uid != profile.uid) return false

        profileLoadJob?.cancel()
        stateController.authenticate(profile.copy(email = currentUser.email ?: profile.email))
        return true
    }

    suspend fun updateProfile(uid: String, ownerName: String, businessName: String, phone: String): Boolean {
        val currentUser = firebaseAuth.currentUser ?: return false
        if (currentUser.uid != uid || !stateController.isAuthenticatedAs(uid)) return false

        firestore.collection("users").document(uid).set(
            mapOf(
                "uid" to uid,
                "email" to currentUser.email.orEmpty(),
                "ownerName" to ownerName,
                "businessName" to businessName,
                "phone" to phone
            ),
            SetOptions.merge()
        ).await()

        if (firebaseAuth.currentUser?.uid != uid) return false
        return stateController.updateProfile(uid, ownerName, businessName, phone)
    }

    fun isAuthenticatedAs(uid: String): Boolean {
        return firebaseAuth.currentUser?.uid == uid && stateController.isAuthenticatedAs(uid)
    }

    fun logout() {
        profileLoadJob?.cancel()
        firebaseAuth.signOut()
        sessionManager.clearSession()
        stateController.begin(firebaseAuth.currentUser?.uid)
    }

    private fun profileError(error: Exception): String = when (error) {
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Check your internet connection and try again."
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Your saved business profile is unavailable. Please contact support."
            else -> "Your profile could not be loaded. Please try again."
        }
        is com.google.firebase.FirebaseNetworkException -> "Check your internet connection and try again."
        else -> "Your profile could not be loaded. Please try again."
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
