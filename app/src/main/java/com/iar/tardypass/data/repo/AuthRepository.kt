package com.iar.tardypass.data.repo

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.iar.tardypass.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** The signed-in staff member, or null when running unauthenticated. */
data class StaffUser(
    val uid: String,
    val displayName: String,
    val email: String
) {
    /** Fallback initials for the receipt when the user hasn't set their own. */
    val initials: String get() = com.iar.tardypass.domain.initialsFrom(displayName)
}

sealed class SignInResult {
    data class Success(val user: StaffUser) : SignInResult()
    data object Cancelled : SignInResult()
    data class Failed(val message: String) : SignInResult()
    /** No Google account on the device, or Play Services unavailable. */
    data object NoCredentials : SignInResult()
}

/**
 * Google Sign-In through Credential Manager (the non-deprecated path) backed by
 * Firebase Auth.
 *
 * The app deliberately still works signed-out: printing and local logging are
 * never blocked by auth, because a hallway device that can't reach Google must
 * not stop issuing passes. Sync is what requires an identity.
 */
class AuthRepository(
    private val appContext: Context,
    private val webClientId: String,
    private val hostedDomain: String?
) {
    private val enabled = BuildConfig.FIREBASE_CONFIGURED

    private val auth: FirebaseAuth? by lazy {
        if (enabled) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
    }

    private val _currentUser = MutableStateFlow(auth?.currentUser?.toStaffUser())
    val currentUser: StateFlow<StaffUser?> = _currentUser

    val isConfigured: Boolean get() = enabled && auth != null

    suspend fun signIn(activityContext: Context): SignInResult {
        val auth = auth ?: return SignInResult.Failed("Firebase is not configured on this build")
        if (webClientId.isBlank()) {
            return SignInResult.Failed("Missing Google web client ID")
        }

        // Credential Manager has no hosted-domain filter (that was GoogleSignIn,
        // now deprecated), so the domain is checked after sign-in below. Neither
        // is a security boundary — the Firestore member-doc gate is.
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = CredentialManager.create(appContext)
                .getCredential(activityContext, request)
            val credential = response.credential
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(firebaseCredential).await()
            val user = result.user?.toStaffUser()
                ?: return SignInResult.Failed("Sign-in returned no user")

            val domain = hostedDomain?.takeIf { it.isNotBlank() }
            if (domain != null && !user.email.endsWith("@$domain", ignoreCase = true)) {
                auth.signOut()
                _currentUser.value = null
                return SignInResult.Failed("Use your @$domain school account (got ${user.email})")
            }

            _currentUser.value = user
            SignInResult.Success(user)
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.NoCredentials
        } catch (e: GetCredentialException) {
            SignInResult.Failed(e.message ?: "Credential Manager error")
        } catch (e: Exception) {
            SignInResult.Failed(e.message ?: "Sign-in failed")
        }
    }

    fun signOut() {
        auth?.signOut()
        _currentUser.value = null
    }

    fun refresh() {
        _currentUser.value = auth?.currentUser?.toStaffUser()
    }

    private fun com.google.firebase.auth.FirebaseUser.toStaffUser() = StaffUser(
        uid = uid,
        displayName = displayName ?: email?.substringBefore('@') ?: "Staff",
        email = email ?: ""
    )
}
