package com.iar.tardypass.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iar.tardypass.ui.theme.Green600
import com.iar.tardypass.ui.theme.Red600
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Account + sync status, shown in Settings. Signing in is optional by design:
 * the app prints and logs locally without it, so a Google outage can't stop
 * passes going out.
 */
@Composable
fun AccountCard(vm: AuthViewModel) {
    val state by vm.state.collectAsState()
    val sync by vm.syncStatus.collectAsState()
    val role by vm.memberRole.collectAsState()
    val context = LocalContext.current
    val isDevice = role == "device"
    var confirmSignOut by remember { mutableStateOf(false) }

    // An accidental sign-out on a hallway printer is a trip to IT — staff don't
    // have the printer account's password.
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign this printer out?") },
            text = {
                Text(
                    "Signing back in needs the printer account's password, which staff " +
                        "don't have. Passes will still print, but nothing will sync."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.signOut(); confirmSignOut = false }) {
                    Text("Sign out", color = Red600)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Staff account", style = MaterialTheme.typography.titleMedium)

            if (!state.configured) {
                Text(
                    "Cloud sync isn't configured in this build. Passes are logged on this device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                return@Column
            }

            val user = state.user
            if (user == null) {
                Text(
                    "Not signed in — passes are logged on this device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Button(
                    onClick = { vm.signIn(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text("Sign in with Google")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (!isDevice) {
                        OutlinedButton(onClick = { vm.signIn(context) }, enabled = !state.busy) {
                            Text("Switch")
                        }
                    }
                }
                if (isDevice) {
                    Text(
                        "This is a shared printer. Staff choose their own name on the " +
                            "Pass screen — don't sign this account out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", color = if (sync.rosterListening) Green600 else Red600)
                    Text(
                        buildString {
                            append(if (sync.rosterListening) " Roster sync on" else " Roster sync off")
                            sync.lastRosterSyncMillis?.let {
                                append(" · updated ")
                                append(SimpleDateFormat("hh:mm a", Locale.US).format(Date(it)))
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                sync.lastError?.let {
                    Text("Sync error: $it", style = MaterialTheme.typography.bodySmall, color = Red600)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::retrySync) { Text("Retry sync") }
                    OutlinedButton(
                        onClick = { if (isDevice) confirmSignOut = true else vm.signOut() }
                    ) { Text("Sign out") }
                }
            }

            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
