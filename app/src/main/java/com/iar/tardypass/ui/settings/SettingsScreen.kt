package com.iar.tardypass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iar.tardypass.printing.PrinterEncoding

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    authVm: com.iar.tardypass.ui.auth.AuthViewModel? = null
) {
    val settings by vm.settings.collectAsState()
    val availability by vm.availability.collectAsState()
    val testResult by vm.testResult.collectAsState()

    LaunchedEffect(Unit) { vm.refreshAvailability() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        authVm?.let { com.iar.tardypass.ui.auth.AccountCard(it) }

        SectionCard("Printer") {
            vm.driverChoices.forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = settings.printerDriverId == id,
                        onClick = { vm.setDriver(id) },
                        label = { Text(name) }
                    )
                    Spacer(Modifier.weight(1f))
                    if (id != "auto") {
                        val avail = availability[id]
                        Text(
                            when (avail) {
                                true -> "available"
                                false -> "not found"
                                null -> ""
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        TextButton(onClick = { vm.testPrint(id) }) { Text("Test") }
                    }
                }
            }
            testResult?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            Text("Encoding (match printer's code system setting)", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrinterEncoding.entries.forEach { e ->
                    FilterChip(
                        selected = settings.encoding == e,
                        onClick = { vm.setEncoding(e) },
                        label = { Text(e.name) }
                    )
                }
            }
            ToggleRow("Send code-page select command", settings.selectCodePage, vm::setSelectCodePage)
            OutlinedTextField(
                value = settings.serialDevicePath,
                onValueChange = vm::setSerialPath,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Serial device path (from hardware discovery, e.g. /dev/ttyS1)") },
                singleLine = true
            )
        }

        SectionCard("Pass") {
            OutlinedTextField(
                value = settings.passTitle,
                onValueChange = vm::setPassTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pass title") },
                singleLine = true
            )
            ToggleRow("Hide period", settings.hidePeriod, vm::setHidePeriod)
            ToggleRow("Hide course", settings.hideCourse, vm::setHideCourse)
            ToggleRow("Hide room", settings.hideRoom, vm::setHideRoom)
        }

        SectionCard("Records") {
            ToggleRow("Log printed passes", settings.enableLogging, vm::setEnableLogging)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
