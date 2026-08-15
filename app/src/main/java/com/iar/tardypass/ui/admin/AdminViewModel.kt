package com.iar.tardypass.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iar.tardypass.data.remote.FirestoreSync
import com.iar.tardypass.data.repo.AuthRepository
import com.iar.tardypass.domain.Invite
import com.iar.tardypass.domain.Member
import com.iar.tardypass.domain.StaffMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val sync: FirestoreSync,
    private val auth: AuthRepository
) : ViewModel() {

    val members: StateFlow<List<Member>> = sync.observeMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invites: StateFlow<List<Invite>> = sync.observeInvites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** People creditable on a pass. No account, no sign-in — just a name. */
    val staffList: StateFlow<List<StaffMember>> = sync.observeStaff()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addStaff(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (staffList.value.any { it.name.equals(clean, ignoreCase = true) }) {
            message.value = "$clean is already on the staff list"
            return
        }
        run(successMsg = "Added $clean") { sync.addStaff(clean) }
    }

    fun removeStaff(member: StaffMember) =
        run(successMsg = "Removed ${member.name}") { sync.removeStaff(member.id) }

    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    /** The signed-in admin's own uid, so the UI can stop them demoting themselves. */
    val currentUid: String? get() = auth.currentUser.value?.uid

    fun invite(email: String, role: String, schoolDomain: String) {
        val clean = email.trim().lowercase()
        if (clean.isEmpty()) return
        if (!clean.contains('@')) {
            message.value = "Enter a full email address"
            return
        }
        if (schoolDomain.isNotBlank() && !clean.endsWith("@$schoolDomain")) {
            message.value = "Staff must use an @$schoolDomain address"
            return
        }
        run(successMsg = "$clean can now sign in") {
            sync.inviteStaff(clean, role, currentUid)
        }
    }

    fun revokeInvite(email: String) =
        run(successMsg = "Invite for $email revoked") { sync.revokeInvite(email) }

    fun setRole(member: Member, role: String) {
        if (member.uid == currentUid) {
            message.value = "You can't change your own role"
            return
        }
        run(successMsg = "${member.displayName} is now $role") {
            sync.setMemberRole(member.uid, role)
        }
    }

    fun remove(member: Member) {
        if (member.uid == currentUid) {
            message.value = "You can't remove yourself"
            return
        }
        run(successMsg = "${member.displayName} removed") { sync.removeMember(member.uid) }
    }

    fun dismissMessage() { message.value = null }

    private fun run(successMsg: String, block: suspend () -> Result<Unit>) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val result = block()
            busy.value = false
            message.value = result.fold(
                onSuccess = { successMsg },
                onFailure = { "Failed: ${it.message}" }
            )
        }
    }
}
