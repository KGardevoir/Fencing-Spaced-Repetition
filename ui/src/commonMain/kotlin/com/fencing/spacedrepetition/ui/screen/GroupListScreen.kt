// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.fencing.spacedrepetition.ui.components.ConfirmDeleteDialog
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.KeptListPosition
import com.fencing.spacedrepetition.ui.components.ScrollToTopOnSortChange
import com.fencing.spacedrepetition.ui.components.SelectableListTopBar
import com.fencing.spacedrepetition.ui.components.SelectionAction
import com.fencing.spacedrepetition.ui.components.rememberSelectionState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.components.ImportExportDialogs
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
    learningGroups: List<LearningGroup> = emptyList(),
    sortOption: GroupSortOption,
    importExportState: ImportExportState,
    dueCardCountFor: @Composable (Long) -> Int,
    onSetSortOption: (GroupSortOption) -> Unit,
    onDeleteGroups: (List<Group>) -> Unit,
    onDismissImportExport: () -> Unit,
    onImportArchive: (Group) -> Unit,
    onExportArchive: (Group) -> Unit,
    onImportCsv: (Group) -> Unit,
    onExportCsv: (Group) -> Unit,
    pendingCsvTargetGroup: Group? = null,
    onCsvImportInto: (List<ParsedCard>, List<String>, Long) -> Unit = { _, _, _ -> },
    onCsvImportIntoNewGroup: (List<ParsedCard>, List<String>, String) -> Unit = { _, _, _ -> },
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToAdd: () -> Unit = {},
    /** Where the list is scrolled; held by the caller so it survives opening a group. */
    listPosition: KeptListPosition = remember { KeptListPosition() }
) {
    var showDeleteDialog by remember { mutableStateOf<List<Group>?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    val selection = rememberSelectionState(groups.map { it.id })

    // Scroll to top when sort option changes
    val groupListState = listPosition.list
    ScrollToTopOnSortChange(listPosition, sortOption)

    Scaffold(
        topBar = {
            SelectableListTopBar(
                title = "Groups",
                selection = selection,
                selectableIds = groups.map { it.id },
                selectLabel = "Select Groups",
                selectionActions = listOf(
                    SelectionAction("Delete", Icons.Default.Delete, destructive = true) {
                        showDeleteDialog = groups.filter { it.id in selection.selected }
                    }
                ),
                onNavigateBack = onNavigateBack
            ) {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        GroupSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { onSetSortOption(option); showSortMenu = false },
                                leadingIcon = { if (sortOption == option) Icon(Icons.Default.Check, contentDescription = null) }
                            )
                        }
                    }
                }
            }
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
                        AppIcons.CardGroups,
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
                        learningGroupName = learningGroups.find { it.id == group.learningGroupId }?.name,
                        selecting = selection.active,
                        selected = group.id in selection.selected,
                        onClick = {
                            if (selection.active) selection.toggle(group.id) else onNavigateToEdit(group.id)
                        },
                        onLongPress = { if (!selection.active) selection.start(group.id) },
                        onEdit = { onNavigateToEdit(group.id) },
                        onImport = { onImportArchive(group) },
                        onExport = { onExportArchive(group) },
                        onCsvImport = { onImportCsv(group) },
                        onCsvExport = { onExportCsv(group) }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { doomed ->
        val what = doomed.singleOrNull()?.let { "\"${it.name}\"" } ?: "${doomed.size} groups"
        ConfirmDeleteDialog(
            title = if (doomed.size == 1) "Delete Group?" else "Delete Groups?",
            text = "Delete $what? Cards in " + (if (doomed.size == 1) "this group" else "these groups") +
                " are not deleted, only removed from " + (if (doomed.size == 1) "it." else "them."),
            onConfirm = { onDeleteGroups(doomed); showDeleteDialog = null; selection.exit() },
            onDismiss = { showDeleteDialog = null }
        )
    }

    // Import/Export state dialogs
    ImportExportDialogs(
        importExportState, groups, onDismissImportExport, onCsvImportInto, onCsvImportIntoNewGroup,
        // A group-level import already knows where the cards go, so it skips the picker.
        csvTargetGroupId = pendingCsvTargetGroup?.id
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupListItem(
    group: Group,
    dueCardCount: Int,
    learningGroupName: String? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCsvImport: () -> Unit = {},
    onCsvExport: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            } else {
                Icon(
                    AppIcons.CardGroups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (learningGroupName != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            AppIcons.LearningGroup,
                            contentDescription = "Learning Group",
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
                if (learningGroupName != null) {
                    Text(
                        "Learning group: $learningGroupName",
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
            if (!selecting) Box {
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
                }
            }
        }
    }
}
