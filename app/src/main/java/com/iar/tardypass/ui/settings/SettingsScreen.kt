package com.iar.tardypass.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.iar.tardypass.BuildConfig
import com.iar.tardypass.domain.BellSchedule
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iar.tardypass.data.repo.STAFF_TIMEOUT_CHOICES
import com.iar.tardypass.printing.PrinterEncoding
import com.iar.tardypass.ui.auth.AuthViewModel
import com.iar.tardypass.ui.components.ChoiceChip
import com.iar.tardypass.ui.theme.Green600
import com.iar.tardypass.ui.theme.Red600

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    authVm: AuthViewModel? = null,
    rosterCount: Int = 0,
    onOpenImport: (() -> Unit)? = null,
    onOpenAdmin: (() -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()
    val availability by vm.availability.collectAsState()
    val printerStatus by vm.printerStatus.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val role = authVm?.memberRole?.collectAsState()?.value
    val signedIn = authVm?.state?.collectAsState()?.value?.user != null

    // Cloud roster writes are admin-only in the Firestore rules, so a non-admin
    // import would diverge this device from everyone else. Offline/unconfigured
    // deployments have no cloud roster at all, so local import stays available.
    val syncActive = authVm != null && signedIn
    val canImport = !syncActive || role == "admin"

    LaunchedEffect(Unit) { vm.refreshAvailability() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        authVm?.let { com.iar.tardypass.ui.auth.AccountCard(it) }

        // Admin-only entry point. This is convenience, not security — every write
        // it performs is independently checked by the Firestore rules.
        if (role == "admin" && onOpenAdmin != null) {
            CollapsibleCard("Administration", initiallyExpanded = true) {
                Text(
                    "Invite staff, set roles, and remove access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Button(
                    onClick = onOpenAdmin,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Manage staff") }
            }
        }

        CollapsibleCard("Roster", initiallyExpanded = true) {
            Text(
                "$rosterCount active students",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            when {
                canImport && syncActive -> {
                    Text(
                        "Importing updates the roster for every device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = { onOpenImport?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Import roster CSV") }
                }
                canImport -> {
                    Text(
                        "Not signed in — an import applies to this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Button(
                        onClick = { onOpenImport?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Import roster CSV (this device)") }
                }
                else -> Text(
                    "The roster is managed by an administrator and syncs to this device automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        CollapsibleCard(
            "Printer",
            headerAccessory = {
                Text("●", color = if (printerStatus.activeDriverId != null) Green600 else Red600)
                Spacer(Modifier.width(6.dp))
                Text(
                    printerStatus.activeDriverName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        ) {
            vm.driverChoices.forEach { (id, name) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChip(
                        selected = settings.printerDriverId == id,
                        onClick = { vm.setDriver(id) },
                        label = name
                    )
                    Spacer(Modifier.weight(1f))
                    if (id != "auto") {
                        Text(
                            when (availability[id]) {
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

            Text(
                "Encoding (match printer's code system setting)",
                style = MaterialTheme.typography.labelMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrinterEncoding.entries.forEach { e ->
                    ChoiceChip(
                        selected = settings.encoding == e,
                        onClick = { vm.setEncoding(e) },
                        label = e.name
                    )
                }
            }
            ToggleRow("Send code-page select command", settings.selectCodePage, vm::setSelectCodePage)
            OutlinedTextField(
                value = settings.serialDevicePath,
                onValueChange = vm::setSerialPath,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Serial device path (e.g. /dev/ttyS1)") },
                singleLine = true
            )
        }

        CollapsibleCard("Pass") {
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
            ToggleRow("Hide reason", settings.hideReason, vm::setHideReason)
            if (settings.hideReason) {
                Text(
                    "The reason picker is hidden and every pass is recorded as \"Tardy\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        if (role == "device") {
            CollapsibleCard("Staff selection", initiallyExpanded = true) {
                Text(
                    "Clear the selected staff member after this much inactivity, so the " +
                        "next person doesn't print under someone else's name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                // FlowRow, not Row: five windows at these labels overrun a 720px
                // screen, and the old fixed Row simply clipped the last chip off
                // the edge where nothing revealed it was there.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STAFF_TIMEOUT_CHOICES.forEach { (mins, label) ->
                        ChoiceChip(
                            selected = settings.staffTimeoutMinutes == mins,
                            onClick = { vm.setStaffTimeoutMinutes(mins) },
                            label = label
                        )
                    }
                }
            }
        }

        // Read-only here: entering a schedule is a typing-heavy once-a-year job
        // that belongs on a real keyboard. This view is how staff answer "why did
        // it pick P3?".
        CollapsibleCard("Bell schedule") {
            val bell = remember(settings.bellScheduleJson) {
                BellSchedule.fromJson(settings.bellScheduleJson)
            }
            if (!bell.isConfigured) {
                Text(
                    "Not configured — the period isn't preselected and minutes late " +
                        "won't print. An administrator sets this on the attendance dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                bell.periods.forEach { p ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(p.name, Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
                        Text("${p.start} – ${p.end}")
                    }
                }
                Text(
                    "Managed on the attendance dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        CollapsibleCard("Records") {
            ToggleRow("Log printed passes", settings.enableLogging, vm::setEnableLogging)
        }

        // Sideloaded onto printers by hand, so "did that one actually update?" is
        // a question someone has to answer while standing in a hallway. Printed
        // plainly rather than hidden behind a tap.
        Text(
            "TardyPass ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Settings section that collapses to its header. Printing and pass options are
 * set once per device and then rarely touched, so they default to collapsed and
 * keep the screen scannable on a small handheld.
 */
@Composable
private fun CollapsibleCard(
    title: String,
    initiallyExpanded: Boolean = false,
    /** Shown right-aligned in the header, so it reads while the card is collapsed. */
    headerAccessory: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                headerAccessory?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            }
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
