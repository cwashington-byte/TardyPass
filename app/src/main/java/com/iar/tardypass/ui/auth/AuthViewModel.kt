package com.iar.tardypass.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.remote.FirestoreSync
import com.iar.tardypass.data.repo.AuthRepository
import com.iar.tardypass.data.repo.SignInResult
import com.iar.tardypass.data.repo.StaffUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val user: StaffUser? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val configured: Boolean = true
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val sync: FirestoreSync
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(user = auth.currentUser.value, configured = auth.isConfigured)
    )
    val state: StateFlow<AuthUiState> = _state

    val syncStatus = sync.status
    val memberRole = sync.memberRole

    init {
        viewModelScope.launch {
            auth.currentUser.collect { u -> _state.value = _state.value.copy(user = u) }
        }
    }

    fun signIn(activityContext: Context) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val msg = when (val r = auth.signIn(activityContext)) {
                is SignInResult.Success -> {
                    sync.startRosterListener()
                    sync.startMemberListener(r.user.uid)
                    "Signed in as ${r.user.displayName}"
                }
                is SignInResult.Cancelled -> null
                is SignInResult.NoCredentials ->
                    "No Google account on this device. Add one in Android Settings first."
                is SignInResult.Failed -> r.message
            }
            _state.value = _state.value.copy(busy = false, message = msg)
        }
    }

    /** Re-subscribe after fixing permissions server-side, without restarting the app. */
    fun retrySync() {
        sync.retryRosterListener()
    }

    fun signOut() {
        auth.signOut()
        sync.stopRosterListener()
        sync.stopMemberListener()
        _state.value = _state.value.copy(message = "Signed out")
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
