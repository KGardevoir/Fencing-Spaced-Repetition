// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.ui.screen.OpponentEditorDialog
import kotlinx.coroutines.launch

/**
 * Compact dropdown for selecting the opponent a review was performed against.
 * Includes a "None" option and an inline "Add new…" action. When an opponent is
 * selected an edit button appears so the skill multiplier can be adjusted inline.
 * New opponents are created via [onCreate]; difficulty changes go through [onUpdateDifficulty].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentPicker(
    selectedOpponentId: Long?,
    opponents: List<Opponent>,
    onOpponentSelected: (Long?) -> Unit,
    onCreate: suspend (name: String, skillMultiplier: Double) -> Long,
    onUpdateDifficulty: ((opponentId: Long, newMultiplier: Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String = "Opponent"
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showEditDifficulty by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val selected = opponents.find { it.id == selectedOpponentId }
    val displayText = when {
        selectedOpponentId == null -> "None"
        selected != null -> "${selected.name} · ×${"%.2f".format(selected.skillMultiplier)}"
        else -> "[deleted]"
    }

    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            onOpponentSelected(null)
                            expanded = false
                        }
                    )
                    if (opponents.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    opponents.forEach { opponent ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(opponent.name)
                                    Text(
                                        "×${"%.2f".format(opponent.skillMultiplier)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onOpponentSelected(opponent.id)
                                expanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Add new…") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            expanded = false
                            showCreate = true
                        }
                    )
                }
            }

            // Edit difficulty button — only shown when a known opponent is selected
            if (selected != null && onUpdateDifficulty != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = { showEditDifficulty = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit difficulty",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showCreate) {
        OpponentEditorDialog(
            initial = null,
            onDismiss = {
                showCreate = false
                createError = null
            },
            onSave = { name, mult, _ ->
                scope.launch {
                    val id = onCreate(name, mult)
                    if (id < 0) {
                        createError = "An opponent named \"$name\" already exists."
                    } else {
                        onOpponentSelected(id)
                        showCreate = false
                        createError = null
                    }
                }
            },
            errorMessage = createError
        )
    }

    if (showEditDifficulty && selected != null && onUpdateDifficulty != null) {
        OpponentDifficultyDialog(
            opponent = selected,
            onDismiss = { showEditDifficulty = false },
            onSave = { newMultiplier ->
                onUpdateDifficulty(selected.id, newMultiplier)
                showEditDifficulty = false
            }
        )
    }
}

/** Compact dialog for adjusting only an opponent's skill multiplier. */
@Composable
fun OpponentDifficultyDialog(
    opponent: Opponent,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var multiplierText by rememberSaveable(opponent.id) {
        mutableStateOf("%.2f".format(opponent.skillMultiplier))
    }
    val multiplier = multiplierText.toDoubleOrNull()?.coerceIn(0.1, 3.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Difficulty: ${opponent.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = multiplierText,
                    onValueChange = { multiplierText = it },
                    label = { Text("Skill multiplier") },
                    supportingText = { Text("1.0 = neutral. >1.0 harder, <1.0 easier.", style = MaterialTheme.typography.labelSmall) },
                    isError = multiplier == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.5, 0.75, 1.0, 1.25, 1.5).forEach { preset ->
                        AssistChip(
                            onClick = { multiplierText = "%.2f".format(preset) },
                            label = { Text("×${"%.2f".format(preset)}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(multiplier ?: opponent.skillMultiplier) },
                enabled = multiplier != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
