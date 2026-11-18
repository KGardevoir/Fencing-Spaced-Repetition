// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.util.toTwoDecimals

/**
 * Pick two opponents and fold the first into the second: every past review
 * against the first is relabelled as the second, and the first is deleted.
 * For duplicates -- the same person entered twice under different spellings.
 *
 * [initialSourceId] preselects who is merged away and [initialTargetId] who
 * is kept; both pickers stay editable.
 */
@Composable
fun MergeOpponentsDialog(
    opponents: List<Opponent>,
    initialSourceId: Long? = null,
    initialTargetId: Long? = null,
    onDismiss: () -> Unit,
    onMerge: (source: Opponent, target: Opponent) -> Unit
) {
    var sourceId by remember { mutableStateOf(initialSourceId) }
    var targetId by remember { mutableStateOf(initialTargetId) }

    val source = opponents.find { it.id == sourceId }
    val target = opponents.find { it.id == targetId }
    val canMerge = source != null && target != null && source.id != target.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge opponents") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Use this when the same person was entered twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OpponentDropdown(
                    label = "Merge away",
                    opponents = opponents.filter { it.id != targetId },
                    selected = source,
                    onSelect = { sourceId = it.id }
                )
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                OpponentDropdown(
                    label = "Keep",
                    opponents = opponents.filter { it.id != sourceId },
                    selected = target,
                    onSelect = { targetId = it.id }
                )
                if (canMerge) {
                    Text(
                        "All of ${source!!.name}'s history will be relabelled as " +
                            "${target!!.name}, and ${source.name} will be deleted. " +
                            "${target.name}'s name and skill multiplier are kept; " +
                            "past grades are not recomputed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (source != null && target != null) onMerge(source, target) },
                enabled = canMerge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Merge") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpponentDropdown(
    label: String,
    opponents: List<Opponent>,
    selected: Opponent?,
    onSelect: (Opponent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.name ?: "Choose…",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            opponents.forEach { opponent ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(opponent.name)
                            Text(
                                "×${opponent.skillMultiplier.toTwoDecimals()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(opponent)
                        expanded = false
                    }
                )
            }
        }
    }
}
