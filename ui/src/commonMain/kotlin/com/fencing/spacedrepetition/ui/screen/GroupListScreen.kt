// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.components.CsvGroupSelectionDialog
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.ui.viewmodel.GroupSortOption
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState

/**
 * The list of groups.
 *
 * Values and callbacks rather than a view model, so it can live in shared
 * code. The four file pickers went with it: choosing a file is an Android
 * activity result here and a very different thing in a browser, so the screen
 * now only says which group the user asked to import or export, and whoever
 * hosts it decides how a file is chosen.
 *
 * @param dueCardCountFor a composable lookup rather than a map, deliberately.
 *   The count comes from a Flow per group, and the list only composes the rows
 *   on screen; handing over a finished map would start a collector for every
 *   group in the collection whether or not anyone can see it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    groups: List<Group>,
    sortOption: GroupSortOption,
    importExportState: ImportExportState,
    dueCardCountFor: @Composable (Long) -> Int,
    onSetSortOption: (GroupSortOption) -> Unit,
    onDeleteGroup: (Group) -> Unit,
    onDismissImportExport: () -> Unit,
    onImportTsv: (Group) -> Unit,
    onExportTsv: (Group) -> Unit,
    onImportCsv: (Group) -> Unit,
    onExportCsv: (Group) -> Unit,
    pendingCsvTargetGroup: Group? = null,
    onCsvImportInto: (List<ParsedCard>, List<String>, Long) -> Unit = { _, _, _ -> },
    onCsvImportIntoNewGroup: (List<ParsedCard>, List<String>, String) -> Unit = { _, _, _ -> },
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToAdd: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf<Group?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Scroll to top when sort option changes
    val groupListState = rememberLazyListState()
    LaunchedEffect(sortOption) {
        groupListState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            GroupSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSetSortOption(option)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOption == option) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add Group")
            }
        }
    ) { paddingValues ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "No groups yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap + to create your first group",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = groupListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    GroupListItem(
                        group = group,
                        dueCardCount = dueCardCountFor(group.id),
                        onEdit = { onNavigateToEdit(group.id) },
                        onDelete = { showDeleteDialog = group },
                        onImport = { onImportTsv(group) },
                        onExport = { onExportTsv(group) },
                        onCsvImport = { onImportCsv(group) },
                        onCsvExport = { onExportCsv(group) }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { group ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Delete Group?") },
            text = {
                Text("Are you sure you want to delete \"${group.name}\"?\n\nCards in this group will NOT be deleted, only removed from this group.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup(group)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import/Export state dialogs
    when (val state = importExportState) {
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
                onDismissRequest = { onDismissImportExport() },
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
                    Button(onClick = { onDismissImportExport() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = { onDismissImportExport() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} cards.") },
                confirmButton = {
                    Button(onClick = { onDismissImportExport() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.PhotoExportSuccess -> {
            AlertDialog(
                onDismissRequest = { onDismissImportExport() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} photos.") },
                confirmButton = {
                    Button(onClick = { onDismissImportExport() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.Error -> {
            AlertDialog(
                onDismissRequest = { onDismissImportExport() },
                icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { onDismissImportExport() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.CsvPendingGroupSelection -> {
            // A group-level import already knows where the cards go, so it
            // skips the picker. Which group that is belongs to whoever
            // launched the file chooser, so it arrives as a parameter.
            val targetGroup = pendingCsvTargetGroup
            if (targetGroup != null) {
                LaunchedEffect(state) {
                    onCsvImportInto(state.parsedCards, state.parseErrors, targetGroup.id)
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
                    onDismiss = { onDismissImportExport() }
                )
            }
        }
        is ImportExportState.Idle -> { /* No dialog */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListItem(
    group: Group,
    dueCardCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCsvImport: () -> Unit = {},
    onCsvExport: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (group.independentLearning) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.School,
                            contentDescription = "Independent Learning",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    if (group.hasCustomSettings()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Custom Settings",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                if (group.description.isNotEmpty()) {
                    Text(
                        group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group.independentLearning) {
                    Text(
                        "Independent learning enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (dueCardCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(dueCardCount.toString())
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import Cards") },
                        onClick = {
                            showMenu = false
                            onImport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Cards") },
                        onClick = {
                            showMenu = false
                            onExport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Import CSV") },
                        onClick = {
                            showMenu = false
                            onCsvImport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.TableChart, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export CSV") },
                        onClick = {
                            showMenu = false
                            onCsvExport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.GridOn, contentDescription = null)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
