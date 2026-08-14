package com.iar.tardypass.ui.logs

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iar.tardypass.ui.components.ChoiceChip
import com.iar.tardypass.ui.theme.Green600
import com.iar.tardypass.ui.theme.Red600
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(vm: LogsViewModel) {
    val logs by vm.logs.collectAsState()
    val todayOnly by vm.todayOnly.collectAsState()
    val logScope by vm.scope.collectAsState()
    val total by vm.totalCount.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) scope.launch {
            val csv = vm.exportCsv()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(csv.toByteArray(Charsets.UTF_8))
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all logs?") },
            text = { Text("This permanently deletes all $total pass records on this device.") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); confirmClear = false }) {
                    Text("Delete all", color = Red600)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(selected = todayOnly, onClick = { vm.todayOnly.value = true }, label = "Today")
            ChoiceChip(selected = !todayOnly, onClick = { vm.todayOnly.value = false }, label = "All")
            Spacer(Modifier.weight(1f))
            if (vm.syncAvailable) {
                ChoiceChip(
                    selected = logScope == LogScope.SCHOOL,
                    onClick = {
                        vm.scope.value =
                            if (logScope == LogScope.SCHOOL) LogScope.DEVICE else LogScope.SCHOOL
                    },
                    label = if (logScope == LogScope.SCHOOL) "All devices" else "This device"
                )
            }
            TextButton(onClick = {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("tardy_export_$date.csv")
            }) { Text("Export CSV") }
            TextButton(onClick = { confirmClear = true }) { Text("Clear", color = Red600) }
        }

        if (logs.isEmpty()) {
            Text(
                "No passes recorded${if (todayOnly) " today" else ""}.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs, key = { it.remoteId ?: "local-${it.id}" }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${p.studentLastName}, ${p.studentFirstName}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                buildString {
                                    append(SimpleDateFormat("MM/dd hh:mm a", Locale.US).format(Date(p.timestampMillis)))
                                    if (p.period.isNotBlank()) append(" · ${p.period}")
                                    p.reason?.let { append(" · $it") }
                                    // The account name where we have one, else typed
                                    // initials. On a shared printer this is the name
                                    // the person selected — self-reported, not proven.
                                    (p.staffName ?: p.staffInitials)?.let { append(" · $it") }
                                    p.deviceName?.let { append(" · via $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            if (p.excused) "EXCUSED" else "UNEXCUSED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (p.excused) Green600 else Red600
                        )
                    }
                }
            }
        }
    }
}
