package com.example.microbusinesssystem.data.repository

import android.util.Log
import com.example.microbusinesssystem.domain.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.firestore.SetOptions


data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val ownerName: String = "",
    val businessName: String = "",
    val phone: String = ""
)

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private companion object {
        const val TAG = "AuthRepository"
        const val PROFILE_READ_RETRIES = 2
        const val PROFILE_READ_RETRY_DELAY_MS = 300L
    }

    suspend fun login(email: String, password: String): Resource<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                    ?: return@withContext Resource.Error("Authentication failed.")
                val profile = try {
                    loadProfile(user.uid, user.email.orEmpty())
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    logFailure("profile read or recovery", error)
                    return@withContext Resource.Error(profileReadError(error))
                }
                Resource.Success(profile)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logFailure("sign in", e)
                Resource.Error(AuthErrorMapper.message(e, registration = false))
            }
        }
    }

    suspend fun register(
        email: String,
        password: String,
        ownerName: String,
        businessName: String,
        phone: String
    ): Resource<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val uid = authResult.user?.uid ?: return@withContext Resource.Error("Registration failed.")

                val profile = UserProfile(
                    uid = uid,
                    email = email,
                    ownerName = ownerName,
                    businessName = businessName,
                    phone = phone
                )

                try {
                    firestore.collection("users").document(uid).set(profile).await()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    logFailure("profile create", error)
                    val accountRolledBack = rollbackNewAccount(authResult.user)
                    throw ProfileWriteException(error, accountRolledBack)
                }

                Resource.Success(profile)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logFailure("registration", e)
                if (e is ProfileWriteException) {
                    return@withContext Resource.Error(e.userMessage)
                }
                Resource.Error(AuthErrorMapper.message(e, registration = true))
            }
        }
    }

    /** Shared profile read for interactive login and Firebase session restoration. */

    /**
     * Loads a user's Firestore profile.
     *
     * If the profile does not exist, it creates a basic profile
     * automatically using the authenticated Firebase UID.
     */
    suspend fun loadProfile(uid: String, authEmail: String): UserProfile {

        val documentRef = firestore
            .collection("users")
            .document(uid)

        return firestore.runTransaction { transaction ->

            val document = transaction.get(documentRef)

            if (document.exists()) {

                val existingProfile =
                    document.toObject(UserProfile::class.java)
                        ?: UserProfile()

                existingProfile.copy(
                    uid = uid,
                    email = authEmail.ifBlank {
                        existingProfile.email
                    }
                )

            } else {

                // No Firestore profile exists.
                // Automatically create a basic one.

                val recoveredProfile = UserProfile(
                    uid = uid,
                    email = authEmail,
                    ownerName = "",
                    businessName = "",
                    phone = ""
                )

                transaction.set(
                    documentRef,
                    recoveredProfile
                )

                recoveredProfile
            }

        }.await()
    }


    fun getCurrentUserUid(): String? {
        return firebaseAuth.currentUser?.uid
    }

    fun logout() {
        firebaseAuth.signOut()
    }

    private fun profileReadError(error: Exception): String = when (error) {
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Check your internet connection and try again."
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Your saved business profile is unavailable. Please contact support."
            else -> "Your profile could not be loaded. Please try again."
        }
        is com.google.firebase.FirebaseNetworkException -> "Check your internet connection and try again."
        else -> "Your profile could not be loaded. Please try again."
    }

    private suspend fun readProfileDocument(uid: String): DocumentSnapshot {
        var attempt = 0
        while (true) {
            try {
                return firestore.collection("users").document(uid).get().await()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val retryable = error is com.google.firebase.FirebaseNetworkException ||
                    (error as? FirebaseFirestoreException)?.code in setOf(
                        FirebaseFirestoreException.Code.UNAVAILABLE,
                        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                    )
                if (!retryable || attempt >= PROFILE_READ_RETRIES) throw error
                delay(PROFILE_READ_RETRY_DELAY_MS * (attempt + 1))
                attempt++
            }
        }
    }

    private suspend fun rollbackNewAccount(user: com.google.firebase.auth.FirebaseUser?): Boolean {
        val uid = user?.uid ?: return false
        if (firebaseAuth.currentUser?.uid != uid) return false
        return try {
            user.delete().await()
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logFailure("account rollback after profile write failure", error)
            if (firebaseAuth.currentUser?.uid == uid) firebaseAuth.signOut()
            false
        }
    }

    private fun logFailure(operation: String, error: Exception) {
        val code = (error as? FirebaseAuthException)?.errorCode
            ?: (error as? FirebaseFirestoreException)?.code?.name
            ?: "unavailable"
        Log.w(TAG, "$operation failed (type=${error.javaClass.simpleName}, code=$code)")
    }

    private class ProfileWriteException(
        cause: Exception,
        accountRolledBack: Boolean
    ) : Exception("Profile document write failed.", cause) {
        val userMessage = when {
            cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                if (accountRolledBack) {
                    "We couldn't save your business profile because access was denied. Please contact support."
                } else {
                    "Your account was created, but profile access was denied. Please contact support before trying again."
                }
            accountRolledBack ->
                "We couldn't save your business profile, so account creation was cancelled. Check your connection and try again."
            else ->
                "Your account was created, but profile setup could not finish. Please contact support before trying again."
        }
    }

}
