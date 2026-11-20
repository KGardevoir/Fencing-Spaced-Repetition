// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.KeptListPosition
import com.fencing.spacedrepetition.ui.components.ScrollToTopOnSortChange
import com.fencing.spacedrepetition.ui.components.SelectionAction
import com.fencing.spacedrepetition.ui.components.SelectionTopBar
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.viewmodel.CardSortOption
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState
import com.fencing.spacedrepetition.ui.viewmodel.SortDirection
import com.fencing.spacedrepetition.ui.components.CsvGroupSelectionDialog
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.formatDate
import com.fencing.spacedrepetition.util.toOneDecimal
import com.fencing.spacedrepetition.util.Time

@Composable
fun BulkGroupDialog(
    groups: List<Group>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.CardGroups, contentDescription = null) },
        title = { Text("Change Groups") },
        text = {
            Column {
                Text(
                    "Select groups for the selected cards:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (groups.isEmpty()) {
                    Text(
                        "No groups available. Create a group first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = group.id in selectedGroupIds,
                                onCheckedChange = { checked ->
                                    selectedGroupIds = if (checked) {
                                        selectedGroupIds + group.id
                                    } else {
                                        selectedGroupIds - group.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(group.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedGroupIds.toList()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportGroupSelectionDialog(
    groups: List<Group>,
    /** Ticked to start with: the group filter chips active on the card list. */
    initiallySelected: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedGroupIds by remember {
        mutableStateOf(initiallySelected.filter { id -> groups.any { it.id == id } }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.CardGroups, contentDescription = null) },
        title = { Text("Export Selected Groups") },
        text = {
            Column {
                Text(
                    "Select groups to export:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (groups.isEmpty()) {
                    Text(
                        "No groups available. Create a group first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(groups) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = group.id in selectedGroupIds,
                                    onCheckedChange = { checked ->
                                        selectedGroupIds = if (checked) {
                                            selectedGroupIds + group.id
                                        } else {
                                            selectedGroupIds - group.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = group.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (group.description.isNotBlank()) {
                                        Text(
                                            text = group.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedGroupIds.toList()) },
                enabled = selectedGroupIds.isNotEmpty()
            ) {
                Text("Export ${selectedGroupIds.size} Groups")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BulkResetDialog(
    selectedCardIds: Set<Long>,
    allCardsWithGroups: List<CardWithGroups>,
    learningGroups: List<LearningGroup>,
    onDismiss: () -> Unit,
    onResetGlobal: () -> Unit,
    onResetGroups: (Set<Long>) -> Unit,
    onResetBoth: (Set<Long>) -> Unit
) {
    // Every learning group any of the selected cards is learnt in
    val affectedLearningGroups = remember(selectedCardIds, allCardsWithGroups, learningGroups) {
        val ids = allCardsWithGroups
            .filter { it.card.id in selectedCardIds }
            .flatMap { it.groups }
            .mapNotNull { it.learningGroupId }
            .toSet()
        learningGroups.filter { it.id in ids }.sortedBy { it.name.lowercase() }
    }

    // Learning group ids to reset
    var selectedGroupIds by remember(affectedLearningGroups) { mutableStateOf(emptySet<Long>()) }

    if (affectedLearningGroups.isEmpty()) {
        // Simple dialog for cards not in independent learning groups
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("Reset Learning State?") },
            text = {
                Text("Reset the learning state for ${selectedCardIds.size} card${if (selectedCardIds.size > 1) "s" else ""}? This will set them back to \"New\" status and clear all progress.")
            },
            confirmButton = {
                Button(
                    onClick = onResetGlobal,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else {
        // Enhanced dialog with group selection
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("Reset Learning State") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Resetting ${selectedCardIds.size} card${if (selectedCardIds.size > 1) "s" else ""}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Some cards are learnt in ${affectedLearningGroups.size} learning group${if (affectedLearningGroups.size > 1) "s" else ""} as well as Global.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Select learning groups to reset:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Group selection checkboxes
                    affectedLearningGroups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGroupIds = if (group.id in selectedGroupIds) {
                                        selectedGroupIds - group.id
                                    } else {
                                        selectedGroupIds + group.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = group.id in selectedGroupIds,
                                onCheckedChange = { checked ->
                                    selectedGroupIds = if (checked) {
                                        selectedGroupIds + group.id
                                    } else {
                                        selectedGroupIds - group.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick select buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                selectedGroupIds = affectedLearningGroups.map { it.id }.toSet()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = { selectedGroupIds = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Reset Global State Only
                    Button(
                        onClick = onResetGlobal,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Global State Only")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset Selected Group States Only
                    OutlinedButton(
                        onClick = { onResetGroups(selectedGroupIds) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedGroupIds.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Selected Learning Groups Only (${selectedGroupIds.size})")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset Both
                    OutlinedButton(
                        onClick = { onResetBoth(selectedGroupIds) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedGroupIds.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset Both Global & Learning Groups (${selectedGroupIds.size})")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
