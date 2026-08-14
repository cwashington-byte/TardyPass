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
import androidx.compose.material3.FilterChip
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
import com.iar.tardypass.ui.theme.Red600

@Composable
fun AdminScreen(vm: AdminViewModel, schoolDomain: String, onBack: () -> Unit) {
    val members by vm.members.collectAsState()
    val invites by vm.invites.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()

    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("staff") }
    var pendingRemoval by remember { mutableStateOf<Member?>(null) }

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
                        FilterChip(
                            selected = role == "staff",
                            onClick = { role = "staff" },
                            label = { Text("Staff") }
                        )
                        FilterChip(
                            selected = role == "admin",
                            onClick = { role = "admin" },
                            label = { Text("Admin") }
                        )
                    }
                    Text(
                        if (role == "admin")
                            "Admins can import rosters and manage staff."
                        else "Staff can search students and print passes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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

        item(key = "staff-h") {
            Text("Staff (${members.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(members, key = { it.uid }) { member ->
            val isSelf = member.uid == vm.currentUid
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!isSelf) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = member.role == "staff",
                                onClick = { vm.setRole(member, "staff") },
                                label = { Text("Staff") }
                            )
                            FilterChip(
                                selected = member.role == "admin",
                                onClick = { vm.setRole(member, "admin") },
                                label = { Text("Admin") }
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
