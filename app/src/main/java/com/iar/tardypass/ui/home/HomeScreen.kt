package com.iar.tardypass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.iar.tardypass.ui.components.ChoiceChip
import com.iar.tardypass.ui.theme.Green600
import com.iar.tardypass.ui.theme.Red600
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsState()
    val printerStatus by vm.printerStatus.collectAsState()

    // Once a student is picked the keyboard is just covering the pass form, so
    // drop focus to dismiss it. Tapping the search field again refocuses and
    // brings it straight back.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    // After a print the student is cleared, so hand the cursor straight back to
    // search — the next action is always looking up another student.
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(state.focusSearchTick) {
        if (state.focusSearchTick > 0) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }

    state.form?.let { form ->
        if (form.pendingDuplicateExcused != null) {
            AlertDialog(
                onDismissRequest = vm::cancelDuplicate,
                title = { Text("Possible duplicate") },
                text = {
                    Text(
                        "${form.student.fullName} already got a pass for " +
                            "${form.period} in the last 10 minutes. Print another?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = vm::confirmDuplicatePrint) { Text("Print anyway") }
                },
                dismissButton = { TextButton(onClick = vm::cancelDuplicate) { Text("Cancel") } }
            )
        }
    }

    if (state.staffPickerOpen) {
        StaffPickerSheet(
            choices = state.staffChoices,
            hasSelection = state.selectedStaff != null,
            onSelect = { dismissKeyboard(); vm.onStaffSelected(it) },
            onClear = vm::clearSelectedStaff,
            onDismiss = vm::dismissStaffPicker
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "printer-chip") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (printerStatus.activeDriverId != null) Green600 else Red600
                Text("●", color = color)
                Spacer(Modifier.width(6.dp))
                Text(printerStatus.activeDriverName, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.rosterCount} students",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (state.deviceMode) {
            item(key = "staff-bar") {
                StaffBar(
                    selected = state.selectedStaff,
                    onClick = { dismissKeyboard(); vm.openStaffPicker() }
                )
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocus),
                placeholder = { Text("Search name or student number…") },
                singleLine = true
            )
        }

        state.banner?.let { banner ->
            item(key = "banner") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.dismissBanner() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (banner.isError) Red600.copy(alpha = 0.12f)
                        else Green600.copy(alpha = 0.12f)
                    )
                ) {
                    Text(
                        banner.text,
                        modifier = Modifier.padding(12.dp),
                        color = if (banner.isError) Red600 else Green600,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        state.form?.let { form ->
            item(key = "form-${form.student.id}") {
                PassFormCard(
                    form = form,
                    initials = state.initials,
                    printing = state.printing,
                    hideReason = state.hideReason,
                    // The picked person's initials already show in the bar above;
                    // a second editable copy of the same fact is a footgun.
                    hideInitials = state.deviceMode && state.selectedStaff != null,
                    onInitials = vm::onInitialsChange,
                    onPeriod = vm::onPeriodChange,
                    onReason = vm::onReasonChange,
                    onCustomReason = vm::onCustomReasonChange,
                    onPrint = vm::print,
                    onClose = vm::clearSelection
                )
            }
        }

        items(state.results, key = { it.id }) { s ->
            val selected = state.form?.student?.id == s.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dismissKeyboard(); vm.selectStudent(s) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${s.id}${s.grade?.let { " · Gr $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (state.query.isNotBlank() && state.results.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.rosterCount == 0) "No roster loaded — import a CSV in the Import tab."
                    else "No matches.",
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PassFormCard(
    form: PassFormState,
    initials: String,
    printing: Boolean,
    hideReason: Boolean,
    hideInitials: Boolean,
    onInitials: (String) -> Unit,
    onPeriod: (String) -> Unit,
    onReason: (String) -> Unit,
    onCustomReason: (String) -> Unit,
    onPrint: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(form.student.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${form.student.id}${form.student.grade?.let { " · Gr $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(onClick = onClose) { Text("Close") }
            }

            if (form.termPassCount > 0) {
                val lastPass = form.recentPasses.firstOrNull()
                val lastText = lastPass?.let {
                    " · last " + SimpleDateFormat("MM/dd hh:mm a", Locale.US)
                        .format(Date(it.timestampMillis))
                } ?: ""
                Text(
                    "${form.termPassCount} pass${if (form.termPassCount == 1) "" else "es"} this year$lastText",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (form.termPassCount >= 5) Red600
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .background(
                            (if (form.termPassCount >= 5) Red600 else MaterialTheme.colorScheme.primary)
                                .copy(alpha = 0.08f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (form.student.schedule.isNotEmpty()) {
                var expanded by remember(form.student.id) { mutableStateOf(false) }
                val periods = form.student.schedule.keys.toList()
                val entry = form.student.schedule[form.period]
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = if (form.period.isBlank()) "" else buildString {
                            append(form.period)
                            entry?.let {
                                if (it.course.isNotBlank()) append(" — ${it.course}")
                                if (it.room.isNotBlank()) append(" (${it.room})")
                            }
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Period") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        periods.forEach { p ->
                            val e = form.student.schedule[p]
                            DropdownMenuItem(
                                text = {
                                    Text(buildString {
                                        append(p)
                                        e?.let {
                                            if (it.course.isNotBlank()) append(" — ${it.course}")
                                            if (it.room.isNotBlank()) append(" (${it.room})")
                                        }
                                    })
                                },
                                onClick = { onPeriod(p); expanded = false }
                            )
                        }
                    }
                }
            }

            if (!hideReason) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REASONS.take(3).forEach { r ->
                        ChoiceChip(selected = form.reason == r, onClick = { onReason(r) }, label = r)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REASONS.drop(3).forEach { r ->
                        ChoiceChip(selected = form.reason == r, onClick = { onReason(r) }, label = r)
                    }
                }
                if (form.reason == "Other") {
                    OutlinedTextField(
                        value = form.customReason,
                        onValueChange = onCustomReason,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reason") },
                        singleLine = true
                    )
                }
            }

            if (!hideInitials) {
                OutlinedTextField(
                    value = initials,
                    onValueChange = onInitials,
                    modifier = Modifier.width(160.dp),
                    label = { Text("Staff initials") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onPrint(true) },
                    enabled = !printing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green600)
                ) {
                    if (printing) CircularProgressIndicator(
                        Modifier.height(20.dp).width(20.dp), color = Color.White, strokeWidth = 2.dp
                    )
                    else Text("PRINT EXCUSED", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onPrint(false) },
                    enabled = !printing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    if (printing) CircularProgressIndicator(
                        Modifier.height(20.dp).width(20.dp), color = Color.White, strokeWidth = 2.dp
                    )
                    else Text("PRINT UNEXCUSED", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
