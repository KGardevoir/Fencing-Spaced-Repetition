// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState
import com.fencing.spacedrepetition.util.ParsedCard

/**
 * What an import or export is doing, as a dialog: working, done (with what
 * was skipped and why), failed, or -- for a CSV import -- which group the
 * cards go to. [csvTargetGroupId] skips that question when the import was
 * started from a group, which already says.
 */
@Composable
fun ImportExportDialogs(
    state: ImportExportState,
    groups: List<Group>,
    onDismiss: () -> Unit,
    onCsvImportInto: (List<ParsedCard>, List<String>, Long) -> Unit,
    onCsvImportIntoNewGroup: (List<ParsedCard>, List<String>, String) -> Unit,
    csvTargetGroupId: Long? = null
) {
    when (state) {
        is ImportExportState.Loading -> {
            AlertDialog(
                onDismissRequest = { },
                icon = { CircularProgressIndicator(modifier = Modifier.size(48.dp)) },
                title = { Text("Processing...") },
                text = { Text("Please wait while the operation completes.") },
                confirmButton = { }
            )
        }
        is ImportExportState.ImportSuccess -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Import Complete") },
                text = {
                    Column {
                        Text("Successfully imported ${state.importedCount} cards.")
                        if (state.skippedCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${state.skippedCount} lines skipped due to errors:",
                                color = MaterialTheme.colorScheme.error
                            )
                            state.errors.take(5).forEach { error ->
                                Text("• $error", style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.errors.size > 5) {
                                Text("...and ${state.errors.size - 5} more", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} cards.") },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.PhotoExportSuccess -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} photos.") },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.CsvPendingGroupSelection -> {
            // A group-level import already knows where the cards go, so it
            // skips the picker. Which group that is belongs to whoever
            // launched the file chooser, so it arrives as a parameter.
            if (csvTargetGroupId != null) {
                LaunchedEffect(state) {
                    onCsvImportInto(state.parsedCards, state.parseErrors, csvTargetGroupId)
                }
            } else {
                CsvGroupSelectionDialog(
                    suggestedGroupName = state.suggestedGroupName,
                    existingGroups = groups,
                    cardCount = state.parsedCards.size,
                    onConfirm = { groupId ->
                        onCsvImportInto(state.parsedCards, state.parseErrors, groupId)
                    },
                    onCreateGroup = { groupName ->
                        onCsvImportIntoNewGroup(state.parsedCards, state.parseErrors, groupName)
                    },
                    onDismiss = onDismiss
                )
            }
        }
        is ImportExportState.Idle -> { /* No dialog */ }
    }
}
