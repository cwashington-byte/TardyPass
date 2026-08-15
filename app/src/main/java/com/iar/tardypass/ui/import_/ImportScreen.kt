package com.iar.tardypass.ui.import_

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iar.tardypass.ui.theme.Red600
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportScreen(vm: ImportViewModel) {
    val state by vm.state.collectAsState()
    val rosterCount by vm.rosterCount.collectAsState(initial = 0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }
            if (text != null) vm.preview(text) else Unit
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Roster: $rosterCount active students",
            style = MaterialTheme.typography.titleMedium
        )

        when (val s = state) {
            is ImportUiState.Idle -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Import roster CSV", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Columns: Student_Number, First_Name, Last_Name, Grade, Schedule " +
                                "(same format as the current SIS export). You can also share a CSV " +
                                "to this app from Files or Drive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = {
                                filePicker.launch(
                                    arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream", "*/*")
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("Choose CSV file") }
                    }
                }
            }

            is ImportUiState.Parsing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.height(24.dp))
                    Text("Working…")
                }
            }

            is ImportUiState.Preview -> {
                val diff = s.diff
                val partial = diff?.looksLikePartialFile() == true
                var confirmCull by remember(s.text) { mutableStateOf(false) }

                if (confirmCull && diff != null) {
                    AlertDialog(
                        onDismissRequest = { confirmCull = false },
                        title = { Text("Deactivate ${diff.deactivateIds.size} students?") },
                        text = {
                            Text(
                                "That is ${(diff.deactivateShare * 100).toInt()}% of the " +
                                    "${diff.activeBefore} students currently active. That is what a " +
                                    "partial export looks like — check this file covers the whole school."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { confirmCull = false; vm.confirm(true) }) {
                                Text("Deactivate anyway", color = Red600)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmCull = false }) { Text("Cancel") }
                        }
                    )
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ready to import", style = MaterialTheme.typography.titleMedium)
                        Text("${s.result.students.size} students parsed")

                        if (s.result.errors.isNotEmpty()) {
                            Text(
                                "${s.result.errors.size} row(s) skipped:",
                                fontWeight = FontWeight.SemiBold,
                                color = Red600
                            )
                            s.result.errors.take(10).forEach { e ->
                                Text(
                                    "Row ${e.row}: ${e.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Red600
                                )
                            }
                            if (s.result.errors.size > 10) {
                                Text(
                                    "…and ${s.result.errors.size - 10} more",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = s.fullReplace,
                                onCheckedChange = { vm.setFullReplace(it) }
                            )
                            Column {
                                Text("This is a complete roster")
                                Text(
                                    "Students missing from this file are marked inactive. Nothing is " +
                                        "ever deleted — their past passes stay in the records.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (s.fullReplace) {
                            Text(
                                when {
                                    diff == null -> "Checking which students would be deactivated…"
                                    diff.deactivateIds.isEmpty() ->
                                        "Nothing will be deactivated."
                                    else ->
                                        "${diff.deactivateIds.size} of ${diff.activeBefore} active " +
                                            "students will be marked inactive."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (partial) Red600
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                // Disabled until the diff lands, so the count is
                                // always on screen before anyone can confirm.
                                enabled = !s.fullReplace || diff != null,
                                onClick = {
                                    if (partial) confirmCull = true else vm.confirm(s.fullReplace)
                                },
                                modifier = Modifier.weight(1f).height(52.dp)
                            ) { Text("Import ${s.result.students.size} students") }
                            OutlinedButton(onClick = vm::reset) { Text("Cancel") }
                        }
                    }
                }
            }

            is ImportUiState.Done -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Imported ${s.imported} students ✓", style = MaterialTheme.typography.titleMedium)
                        if (s.deactivated > 0) Text("${s.deactivated} student(s) marked inactive.")
                        if (s.errorCount > 0) Text("${s.errorCount} row(s) were skipped.")
                        Text(
                            if (s.syncedToCloud) "Synced to all devices."
                            else "Saved on this device only." +
                                (s.syncError?.let { " Cloud sync failed: $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s.syncedToCloud) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else Red600
                        )
                        TextButton(onClick = vm::reset) { Text("Import another file") }
                    }
                }
            }

            is ImportUiState.Error -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import failed", style = MaterialTheme.typography.titleMedium, color = Red600)
                        Text(s.message)
                        TextButton(onClick = vm::reset) { Text("Try again") }
                    }
                }
            }
        }
    }
}
