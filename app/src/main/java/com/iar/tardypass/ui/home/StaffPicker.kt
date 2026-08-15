package com.iar.tardypass.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iar.tardypass.domain.StaffMember
import com.iar.tardypass.ui.theme.Red600

/**
 * Who-is-issuing bar, shown only on shared printers. It sits above the search
 * field rather than inside the pass form because "who am I?" is answered before
 * a student is chosen, and has to be re-checkable at a glance during a handoff.
 */
@Composable
fun StaffBar(
    selected: SelectedStaff?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected == null) Red600.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected == null) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tap to choose your name",
                        fontWeight = FontWeight.Bold,
                        color = Red600
                    )
                    Text(
                        "Passes need to record who issued them",
                        style = MaterialTheme.typography.bodySmall,
                        color = Red600.copy(alpha = 0.8f)
                    )
                }
            } else {
                InitialsBadge(selected.initials)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "Issuing as",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(selected.name, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onClick) { Text("Change") }
            }
        }
    }
}

@Composable
private fun InitialsBadge(initials: String) {
    Box(
        Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials.ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Full-height sheet rather than a dialog: a staff directory can run to dozens of
 * names, and on a handheld the list needs to sit under the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPickerSheet(
    choices: List<StaffMember>,
    hasSelection: Boolean,
    onSelect: (StaffMember) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Who is issuing passes?", style = MaterialTheme.typography.titleLarge)

            if (choices.isEmpty()) {
                Text(
                    "No staff list yet. An administrator adds names under " +
                        "Settings → Administration → Staff list. Until then you can type " +
                        "initials on the pass, which saves it on this device only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                TextButton(onClick = onDismiss) { Text("Close") }
                return@Column
            }

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                label = { Text("Type a few letters") },
                singleLine = true
            )

            if (hasSelection) {
                Text(
                    "Not me — clear",
                    color = Red600,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClear() }
                        .padding(vertical = 14.dp)
                )
                HorizontalDivider()
            }

            val visible = choices
                .filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
                .sortedBy { it.name.lowercase() }

            LazyColumn(
                Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(visible, key = { it.id }) { member ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(member) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InitialsBadge(member.initials)
                        Text(
                            member.name,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        Text(
                            "No one matches \"$filter\".",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
