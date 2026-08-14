package com.iar.tardypass.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iar.tardypass.domain.Member
import com.iar.tardypass.ui.components.ChoiceChip
import com.iar.tardypass.ui.theme.Red600

@Composable
private fun MemberHeader(member: Member, isSelf: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                member.displayName + if (isSelf) " (you)" else "",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Text(
            member.role.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (member.isDevice) Red600 else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AdminScreen(vm: AdminViewModel, schoolDomain: String, onBack: () -> Unit) {
    val members by vm.members.collectAsState()
    val invites by vm.invites.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()

    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("staff") }
    var pendingRemoval by remember { mutableStateOf<Member?>(null) }
    var pendingDeviceConversion by remember { mutableStateOf<Member?>(null) }

    // Marking a real person's account as a device is the one misconfiguration
    // that makes this worse than what it replaced — their personal account could
    // then attribute passes to colleagues. So it's confirmed, never a stray tap.
    pendingDeviceConversion?.let { member ->
        AlertDialog(
            onDismissRequest = { pendingDeviceConversion = null },
            title = { Text("Make ${member.displayName} a device?") },
            text = {
                Text(
                    "Device accounts are for shared printers, not people. Anyone using " +
                        "this account would be able to issue passes under any staff " +
                        "member's name. Only do this for a dedicated printer account."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setRole(member, "device"); pendingDeviceConversion = null
                }) { Text("Make it a device", color = Red600) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeviceConversion = null }) { Text("Cancel") }
            }
        )
    }

    pendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${member.displayName}?") },
            text = {
                Text(
                    "They'll lose access to the roster and won't be able to record passes. " +
                        "Passes they already issued are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.remove(member); pendingRemoval = null }) {
                    Text("Remove", color = Red600)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Manage staff", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Done") }
            }
        }

        item(key = "invite") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Grant access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No email is sent. Access is granted the moment they sign in " +
                            "with this address — just tell them to open the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("School email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip(selected = role == "staff", onClick = { role = "staff" }, label = "Staff")
                        ChoiceChip(selected = role == "admin", onClick = { role = "admin" }, label = "Admin")
                        ChoiceChip(selected = role == "device", onClick = { role = "device" }, label = "Device")
                    }
                    Text(
                        when (role) {
                            "admin" -> "Admins can import rosters and manage staff."
                            "device" -> "A device account is a shared hallway printer, not a person. " +
                                "Staff pick their own name on it, so passes from it are self-reported. " +
                                "Give it its own Workspace account with Gmail and Drive turned off."
                            else -> "Staff can search students and print passes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (role == "device") Red600
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(
                        onClick = { vm.invite(email, role, schoolDomain); email = "" },
                        enabled = !busy && email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Grant access") }
                }
            }
        }

        message?.let {
            item(key = "msg") {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = vm::dismissMessage) { Text("OK") }
                    }
                }
            }
        }

        if (invites.isNotEmpty()) {
            item(key = "pending-h") {
                Text("Waiting for first sign-in", style = MaterialTheme.typography.titleMedium)
            }
            items(invites, key = { "inv-${it.email}" }) { invite ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(invite.email)
                            Text(
                                "Approved as ${invite.role} · hasn't signed in yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        TextButton(onClick = { vm.revokeInvite(invite.email) }) {
                            Text("Revoke", color = Red600)
                        }
                    }
                }
            }
        }

        val (printers, people) = members.partition { it.isDevice }

        item(key = "staff-h") {
            Text("Staff (${people.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(people, key = { it.uid }) { member ->
            val isSelf = member.uid == vm.currentUid
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberHeader(member, isSelf)
                    if (!isSelf) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceChip(
                                selected = member.role == "staff",
                                onClick = { vm.setRole(member, "staff") },
                                label = "Staff"
                            )
                            ChoiceChip(
                                selected = member.role == "admin",
                                onClick = { vm.setRole(member, "admin") },
                                label = "Admin"
                            )
                            ChoiceChip(
                                selected = false,
                                onClick = { pendingDeviceConversion = member },
                                label = "Device"
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { pendingRemoval = member }) {
                                Text("Remove", color = Red600)
                            }
                        }
                    }
                }
            }
        }

        if (printers.isNotEmpty()) {
            item(key = "printers-h") {
                Column {
                    Text("Printers (${printers.size})", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Shared devices. Staff pick their own name on these, so the author " +
                            "on those passes is self-reported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            items(printers, key = { it.uid }) { member ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberHeader(member, isSelf = member.uid == vm.currentUid)
                        Text(
                            "Removing revokes this printer instantly — use it if one is lost.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        // Converting back matters: without it, marking a person as a
                        // device by mistake is a one-way door out of their account.
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceChip(
                                selected = false,
                                onClick = { vm.setRole(member, "staff") },
                                label = "Staff"
                            )
                            ChoiceChip(
                                selected = false,
                                onClick = { vm.setRole(member, "admin") },
                                label = "Admin"
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { pendingRemoval = member }) {
                                Text("Remove", color = Red600)
                            }
                        }
                    }
                }
            }
        }
    }
}
