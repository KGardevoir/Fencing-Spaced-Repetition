// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.util.toTwoDecimals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentsScreen(
    viewModel: OpponentViewModel,
    onNavigateBack: () -> Unit
) {
    val opponents by viewModel.opponents.collectAsState()
    val error by viewModel.error.collectAsState()

    var editing by remember { mutableStateOf<Opponent?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Opponent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opponents") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Opponent")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (opponents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "No opponents yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add opponents to tag your reviews and scale FSRS stability gain by skill level.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(opponents, key = { it.id }) { opponent ->
                        OpponentRow(
                            opponent = opponent,
                            onEdit = { editing = opponent },
                            onDelete = { pendingDelete = opponent }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        OpponentEditorDialog(
            initial = null,
            onDismiss = {
                showAdd = false
                viewModel.clearError()
            },
            onSave = { name, mult, notes ->
                viewModel.addOpponent(name, mult, notes) {
                    showAdd = false
                }
            },
            errorMessage = error
        )
    }

    editing?.let { target ->
        OpponentEditorDialog(
            initial = target,
            onDismiss = {
                editing = null
                viewModel.clearError()
            },
            onSave = { name, mult, notes ->
                viewModel.updateOpponent(
                    target.copy(name = name, skillMultiplier = mult, notes = notes)
                ) {
                    editing = null
                }
            },
            errorMessage = error
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${target.name}?") },
            text = { Text("Past review history will keep the opponent ID but show as \"[deleted]\".") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOpponent(target)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OpponentRow(
    opponent: Opponent,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = opponent.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Skill × ${opponent.skillMultiplier.toTwoDecimals()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (opponent.notes.isNotBlank()) {
                    Text(
                        text = opponent.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

/**
 * Dialog for creating or editing an opponent. Accepts a [skillMultiplier] between 0.1
 * and 3.0; values outside that range are clamped.
 */
@Composable
fun OpponentEditorDialog(
    initial: Opponent?,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit,
    errorMessage: String? = null
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var multiplierText by rememberSaveable(initial?.id) {
        mutableStateOf((initial?.skillMultiplier ?: 1.0).toTwoDecimals())
    }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes ?: "") }

    val multiplier = multiplierText.toDoubleOrNull()?.coerceIn(0.1, 3.0)
    val canSave = name.isNotBlank() && multiplier != null

    val isDirty = name != (initial?.name ?: "") ||
        multiplierText != (initial?.skillMultiplier ?: 1.0).toTwoDecimals() ||
        notes != (initial?.notes ?: "")
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

    fun requestDismiss() {
        if (isDirty) {
            showUnsavedChangesDialog = true
        } else {
            onDismiss()
        }
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Leaving now will discard them.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text(if (initial == null) "Add Opponent" else "Edit Opponent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = multiplierText,
                    onValueChange = { multiplierText = it },
                    label = { Text("Skill multiplier") },
                    supportingText = {
                        Text(
                            "1.0 = neutral. >1.0 harder, <1.0 easier.",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    isError = multiplier == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick preset chips — horizontally scrollable so they never wrap.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.5, 0.75, 1.0, 1.25, 1.5).forEach { preset ->
                        AssistChip(
                            onClick = { multiplierText = preset.toTwoDecimals() },
                            label = { Text("×${preset.toTwoDecimals()}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), multiplier ?: 1.0, notes) },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { requestDismiss() }) { Text("Cancel") }
        }
    )
}
