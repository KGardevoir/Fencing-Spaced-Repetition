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
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.viewmodel.CardSortOption
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState
import com.fencing.spacedrepetition.ui.viewmodel.SortDirection
import com.fencing.spacedrepetition.ui.components.CsvGroupSelectionDialog
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.formatDate
import com.fencing.spacedrepetition.util.toOneDecimal
import com.fencing.spacedrepetition.util.Time

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun CardListScreen(
    allCards: List<Card>,
    allCardsWithGroups: List<CardWithGroups>,
    groups: List<Group>,
    selectedGroupFilters: Set<Long>,
    showDisabledFilter: Boolean,
    searchQuery: String,
    cardCount: Int,
    importExportState: ImportExportState,
    cardSortOption: CardSortOption,
    sortDirection: SortDirection,
    isSelectionMode: Boolean,
    selectedCardIds: Set<Long>,
    learningStatesFor: @Composable (Long) -> List<CardGroupLearningState>,
    onUpdateSearchQuery: (String) -> Unit,
    onClearGroupFilters: () -> Unit,
    onToggleGroupFilter: (Long) -> Unit,
    onToggleDisabledFilter: () -> Unit,
    onSetCardSortOption: (CardSortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onSelectAllCards: () -> Unit,
    onToggleCardSelection: (Long) -> Unit,
    onSetSelectedCardsDisabled: (Boolean) -> Unit,
    onToggleCardDisabled: (Long) -> Unit,
    onDeleteCard: (Card) -> Unit,
    onDeleteSelectedCards: () -> Unit,
    onUpdateSelectedCardsGroups: (List<Long>) -> Unit,
    onResetSelectedCardsGlobalState: () -> Unit,
    onResetSelectedCardsInGroups: (Set<Long>) -> Unit,
    onResetSelectedCardsBothStates: (Set<Long>) -> Unit,
    onResetImportExportState: () -> Unit,
    onCsvImportInto: (List<ParsedCard>, List<String>, Long) -> Unit,
    onCsvImportIntoNewGroup: (List<ParsedCard>, List<String>, String) -> Unit,
    // The seven file operations. Each one asks the caller to put a file
    // picker in front of the user; where the bytes come from or go is the
    // platform's business, so the screen never sees a Uri.
    onImportArchive: () -> Unit,
    onExportAllArchive: (includeHistory: Boolean) -> Unit,
    onExportGroupsArchive: (groupIds: List<Long>, includeHistory: Boolean) -> Unit,
    onImportCsv: () -> Unit,
    onExportAllCsv: () -> Unit,
    onExportGroupsCsv: (groupIds: List<Long>) -> Unit,
    onExportAllPhotos: () -> Unit,
    onNavigateToAddCard: (Long?) -> Unit,
    onNavigateToEditCard: (Card) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<Card?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkGroupDialog by remember { mutableStateOf(false) }
    var showBulkResetDialog by remember { mutableStateOf(false) }
    var showGroupSelectionDialog by remember { mutableStateOf(false) }
    var showCsvGroupSelectionForExport by remember { mutableStateOf(false) }
    var showExportHistoryDialog by remember { mutableStateOf(false) }
    var exportHistoryPendingGroupIds by remember { mutableStateOf<List<Long>?>(null) } // null = export all
    var includeHistoryInExport by remember { mutableStateOf(false) }

    // Handle back press when in selection mode
    if (isSelectionMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    // Scroll to top when sort option or direction changes
    val cardListState = rememberLazyListState()
    LaunchedEffect(cardSortOption, sortDirection) {
        cardListState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = { Text("${selectedCardIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onExitSelectionMode) {
                            Icon(Icons.Default.Close, "Exit selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAllCards) {
                            Icon(Icons.Default.SelectAll, "Select all")
                        }
                        IconButton(
                            onClick = { showBulkGroupDialog = true },
                            enabled = selectedCardIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Folder, "Change groups")
                        }
                        IconButton(
                            onClick = { showBulkResetDialog = true },
                            enabled = selectedCardIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Refresh, "Reset state")
                        }
                        IconButton(
                            onClick = { onSetSelectedCardsDisabled(true) },
                            enabled = selectedCardIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Block, "Disable selected")
                        }
                        IconButton(
                            onClick = { onSetSelectedCardsDisabled(false) },
                            enabled = selectedCardIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.PlayArrow, "Enable selected")
                        }
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            enabled = selectedCardIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "Delete selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            } else {
                // Normal top bar
                TopAppBar(
                    title = { Text("My Cards ($cardCount)") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleSortDirection) {
                            Icon(
                                imageVector = if (sortDirection == SortDirection.ASCENDING)
                                    Icons.Default.ArrowUpward
                                else
                                    Icons.Default.ArrowDownward,
                                contentDescription = "Sort direction"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                CardSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            onSetCardSortOption(option)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (cardSortOption == option) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Select Cards") },
                                    onClick = {
                                        showMenu = false
                                        onToggleSelectionMode()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.CheckBox, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Import Cards") },
                                    onClick = {
                                        showMenu = false
                                        onImportArchive()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileUpload, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export All Cards") },
                                    onClick = {
                                        showMenu = false
                                        exportHistoryPendingGroupIds = null
                                        showExportHistoryDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileDownload, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Selected Groups") },
                                    onClick = {
                                        showMenu = false
                                        showGroupSelectionDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    },
                                    enabled = groups.isNotEmpty()
                                )
                                DropdownMenuItem(
                                    text = { Text("Export All Photos") },
                                    onClick = {
                                        showMenu = false
                                        onExportAllPhotos()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Import CSV") },
                                    onClick = {
                                        showMenu = false
                                        onImportCsv()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.TableChart, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export All as CSV") },
                                    onClick = {
                                        showMenu = false
                                        onExportAllCsv()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.GridOn, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Groups as CSV") },
                                    onClick = {
                                        showMenu = false
                                        showCsvGroupSelectionForExport = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                                    },
                                    enabled = groups.isNotEmpty()
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { onNavigateToAddCard(selectedGroupFilters.firstOrNull()) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add Card")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onUpdateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search cards...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onUpdateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Group + disabled filter chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedGroupFilters.isEmpty() && !showDisabledFilter,
                        onClick = onClearGroupFilters,
                        label = { Text("All") }
                    )
                }
                items(groups) { group ->
                    FilterChip(
                        selected = group.id in selectedGroupFilters,
                        onClick = { onToggleGroupFilter(group.id) },
                        label = { Text(group.name) }
                    )
                }
                item {
                    FilterChip(
                        selected = showDisabledFilter,
                        onClick = onToggleDisabledFilter,
                        label = { Text("Disabled") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Cards list
            if (allCards.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotEmpty()) Icons.Outlined.Search else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                searchQuery.isNotEmpty() -> "No cards match your search"
                                showDisabledFilter && selectedGroupFilters.isNotEmpty() -> "No disabled cards in selected groups"
                                showDisabledFilter -> "No disabled cards"
                                selectedGroupFilters.isNotEmpty() -> "No cards in selected groups"
                                else -> "No cards yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Try a different search term"
                            } else if (showDisabledFilter) {
                                "Disabled cards are hidden from practice sessions"
                            } else {
                                "Tap + to add a card or use menu to import"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = cardListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allCards, key = { it.id }) { card ->
                        // Find groups for this card
                        val cardGroups = allCardsWithGroups
                            .find { it.card.id == card.id }?.groups ?: emptyList()

                        // Get learning states for this card
                        val learningStates = learningStatesFor(card.id)

                        CardListItem(
                            card = card,
                            groups = cardGroups,
                            learningStates = learningStates,
                            isSelectionMode = isSelectionMode,
                            isSelected = card.id in selectedCardIds,
                            sortOption = cardSortOption,
                            onEdit = { onNavigateToEditCard(card) },
                            onDelete = { showDeleteDialog = card },
                            onToggleDisabled = { onToggleCardDisabled(card.id) },
                            onToggleSelection = { onToggleCardSelection(card.id) },
                            onLongPress = {
                                if (!isSelectionMode) {
                                    onToggleSelectionMode()
                                    onToggleCardSelection(card.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { card ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Card?") },
            text = { Text("Are you sure you want to delete this card? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCard(card)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
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
                onDismissRequest = onResetImportExportState,
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
                    Button(onClick = onResetImportExportState) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = onResetImportExportState,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} cards.") },
                confirmButton = {
                    Button(onClick = onResetImportExportState) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.PhotoExportSuccess -> {
            AlertDialog(
                onDismissRequest = onResetImportExportState,
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} photos.") },
                confirmButton = {
                    Button(onClick = onResetImportExportState) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.Error -> {
            AlertDialog(
                onDismissRequest = onResetImportExportState,
                icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = onResetImportExportState) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.CsvPendingGroupSelection -> {
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
                onDismiss = onResetImportExportState
            )
        }
        is ImportExportState.Idle -> { /* No dialog */ }
    }

    // Bulk delete confirmation dialog
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete ${selectedCardIds.size} Cards?") },
            text = { Text("Are you sure you want to delete these cards? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelectedCards()
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk reset state confirmation dialog
    if (showBulkResetDialog) {
        BulkResetDialog(
            selectedCardIds = selectedCardIds,
            allCardsWithGroups = allCardsWithGroups,
            currentGroupFilter = null,
            onDismiss = { showBulkResetDialog = false },
            onResetGlobal = {
                onResetSelectedCardsGlobalState()
                showBulkResetDialog = false
            },
            onResetGroups = { groupIds ->
                onResetSelectedCardsInGroups(groupIds)
                showBulkResetDialog = false
            },
            onResetBoth = { groupIds ->
                onResetSelectedCardsBothStates(groupIds)
                showBulkResetDialog = false
            }
        )
    }

    // Bulk change groups dialog
    if (showBulkGroupDialog) {
        BulkGroupDialog(
            groups = groups,
            onDismiss = { showBulkGroupDialog = false },
            onConfirm = { selectedGroupIds ->
                onUpdateSelectedCardsGroups(selectedGroupIds)
                showBulkGroupDialog = false
            }
        )
    }

    // Export selected groups dialog
    if (showGroupSelectionDialog) {
        ExportGroupSelectionDialog(
            groups = groups,
            onDismiss = { showGroupSelectionDialog = false },
            onConfirm = { selectedGroupIds ->
                showGroupSelectionDialog = false
                exportHistoryPendingGroupIds = selectedGroupIds
                showExportHistoryDialog = true
            }
        )
    }

    // Export history option dialog
    if (showExportHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showExportHistoryDialog = false },
            icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
            title = { Text("Export Options") },
            text = {
                Column {
                    Text("Would you like to include your practice history in the export?")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeHistoryInExport = !includeHistoryInExport },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeHistoryInExport,
                            onCheckedChange = { includeHistoryInExport = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Include practice history")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showExportHistoryDialog = false
                    val pendingGroups = exportHistoryPendingGroupIds
                    if (pendingGroups == null) {
                        onExportAllArchive(includeHistoryInExport)
                    } else {
                        onExportGroupsArchive(pendingGroups, includeHistoryInExport)
                    }
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // CSV export selected groups dialog
    if (showCsvGroupSelectionForExport) {
        ExportGroupSelectionDialog(
            groups = groups,
            onDismiss = { showCsvGroupSelectionForExport = false },
            onConfirm = { selectedGroupIds ->
                showCsvGroupSelectionForExport = false
                onExportGroupsCsv(selectedGroupIds)
            }
        )
    }
}

@Composable
fun BulkGroupDialog(
    groups: List<Group>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
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
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedGroupIds by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CardListItem(
    card: Card,
    groups: List<Group>,
    learningStates: List<CardGroupLearningState> = emptyList(),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sortOption: CardSortOption = CardSortOption.DUE_DATE,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleDisabled: () -> Unit = {},
    onToggleSelection: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        expanded = !expanded
                    }
                },
                onLongClick = onLongPress
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                card.isDisabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection checkbox
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.question,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!expanded) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Due date
                        val dueDateText: String
                        val dueDateColor: androidx.compose.ui.graphics.Color
                        if (card.nextReview == 0L) {
                            dueDateText = "New card"
                            dueDateColor = MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            dueDateText = formatDate(card.nextReview)
                            val now = Time.now()
                            val diff = card.nextReview - now
                            val daysDiff = (diff / (1000 * 60 * 60 * 24)).toInt()
                            dueDateColor = when {
                                diff <= 0 -> MaterialTheme.colorScheme.error
                                daysDiff == 0 -> MaterialTheme.colorScheme.tertiary
                                daysDiff <= 3 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dueDateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = dueDateColor
                            )

                            // Display additional sort field info
                            val reviews = when (card.algorithm) {
                                AlgorithmType.FSRS -> card.fsrsReps
                                AlgorithmType.SM2 -> card.sm2Repetitions
                            }
                            Text(
                                text = "• $reviews reviews",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val difficulty = when (card.algorithm) {
                                AlgorithmType.FSRS -> card.fsrsDifficulty.toOneDecimal()
                                AlgorithmType.SM2 -> (2.5 - card.sm2EaseFactor).toOneDecimal()
                            }
                            Text(
                                text = "• Difficulty: $difficulty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (groups.isNotEmpty()) {
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            groups.firstOrNull()?.name ?: "",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                                if (groups.size > 1) {
                                    Text(
                                        "+${groups.size - 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            AssistChip(
                                onClick = { },
                                label = {
                                    Text(
                                        card.algorithm.name,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                            if (card.isDisabled) {
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            "Disabled",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }

                if (!isSelectionMode) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            if (expanded && !isSelectionMode) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Description:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                MarkdownText(
                    text = card.answer
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Groups display
                if (groups.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groups.forEach { group ->
                            AssistChip(
                                onClick = { },
                                label = { Text(group.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Independent learning states display
                val independentGroups = groups.filter { it.independentLearning }
                if (independentGroups.isNotEmpty()) {
                    Text(
                        text = "Independent Learning:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    independentGroups.forEach { group ->
                        val state = learningStates.find { it.groupId == group.id }
                        val stateText = if (state == null) {
                            "${group.name}: No state"
                        } else {
                            val nextReview = if (state.nextReview == 0L) {
                                "New"
                            } else {
                                formatDate(state.nextReview)
                            }
                            "${group.name}: Next ${nextReview} (${state.fsrsState})"
                        }
                        Text(
                            text = stateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Algorithm chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoChip(
                        icon = Icons.Default.Psychology,
                        label = card.algorithm.name
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Next review info
                val nextReviewText = if (card.nextReview == 0L) {
                    "New card"
                } else {
                    "Next: ${formatDate(card.nextReview)}"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nextReviewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row {
                        IconButton(onClick = onToggleDisabled) {
                            Icon(
                                imageVector = if (card.isDisabled) Icons.Default.PlayArrow else Icons.Default.Block,
                                contentDescription = if (card.isDisabled) "Enable card" else "Disable card",
                                tint = if (card.isDisabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    AssistChip(
        onClick = { },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = Modifier.height(28.dp)
    )
}

@Composable
fun BulkResetDialog(
    selectedCardIds: Set<Long>,
    allCardsWithGroups: List<CardWithGroups>,
    currentGroupFilter: Group?,
    onDismiss: () -> Unit,
    onResetGlobal: () -> Unit,
    onResetGroups: (Set<Long>) -> Unit,
    onResetBoth: (Set<Long>) -> Unit
) {
    // Find all independent learning groups that contain any of the selected cards
    val independentLearningGroups = remember(selectedCardIds, allCardsWithGroups) {
        allCardsWithGroups
            .filter { it.card.id in selectedCardIds }
            .flatMap { it.groups }
            .filter { it.independentLearning }
            .distinctBy { it.id }
            .sortedBy { it.name }
    }

    // Preselect the current filter group if it's an independent learning group
    var selectedGroupIds by remember(currentGroupFilter, independentLearningGroups) {
        val preselected = if (currentGroupFilter != null &&
            independentLearningGroups.any { it.id == currentGroupFilter.id }) {
            setOf(currentGroupFilter.id)
        } else {
            emptySet()
        }
        mutableStateOf(preselected)
    }

    if (independentLearningGroups.isEmpty()) {
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
                        "Some cards are in ${independentLearningGroups.size} group${if (independentLearningGroups.size > 1) "s" else ""} with independent learning.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Select groups to reset:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Group selection checkboxes
                    independentLearningGroups.forEach { group ->
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
                                selectedGroupIds = independentLearningGroups.map { it.id }.toSet()
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
                        Text("Reset Selected Groups Only (${selectedGroupIds.size})")
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
                        Text("Reset Both Global & Groups (${selectedGroupIds.size})")
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
