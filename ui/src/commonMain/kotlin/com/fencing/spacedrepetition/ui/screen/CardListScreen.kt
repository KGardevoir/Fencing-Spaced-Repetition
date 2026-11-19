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
import com.fencing.spacedrepetition.ui.components.ConfirmDeleteDialog
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.KeptListPosition
import com.fencing.spacedrepetition.ui.components.ScrollToTopOnSortChange
import com.fencing.spacedrepetition.ui.components.SelectionAction
import com.fencing.spacedrepetition.ui.components.SelectionTopBar
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningContexts
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.viewmodel.CardSortOption
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState
import com.fencing.spacedrepetition.ui.viewmodel.SortDirection
import com.fencing.spacedrepetition.ui.components.ImportExportDialogs
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
    /** Every concept, for the concept filter row; empty hides the row. */
    concepts: List<com.fencing.spacedrepetition.data.model.Concept> = emptyList(),
    selectedConceptFilters: Set<Long> = emptySet(),
    /** Each card's concepts, by card id, shown on the card the way its groups are. */
    conceptsByCard: Map<Long, List<Pair<com.fencing.spacedrepetition.data.model.Concept, Double>>> = emptyMap(),
    onToggleConceptFilter: (Long) -> Unit = {},
    searchQuery: String,
    cardCount: Int,
    importExportState: ImportExportState,
    cardSortOption: CardSortOption,
    sortDirection: SortDirection,
    isSelectionMode: Boolean,
    selectedCardIds: Set<Long>,
    learningStatesFor: @Composable (Long) -> List<CardLearningState>,
    learningGroups: List<LearningGroup> = emptyList(),
    /** Whether Global schedules by concept; each learning group carries its own switch. */
    globalConceptScheduling: Boolean = false,
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
    onNavigateBack: () -> Unit,
    /** Where the list is scrolled; held by the caller so it survives opening a card. */
    listPosition: KeptListPosition = remember { KeptListPosition() }
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkGroupDialog by remember { mutableStateOf(false) }
    var showBulkResetDialog by remember { mutableStateOf(false) }
    // Resetting a card where concepts schedule would reset the concepts'
    // progress from it too, so reset is offered only where none do.
    val learningContexts = LearningContexts(groups, learningGroups)
    fun schedulesByConcept(cardIds: Collection<Long>): Boolean = cardIds.any { cardId ->
        val cardGroups = allCardsWithGroups.find { it.card.id == cardId }?.groups.orEmpty()
        learningContexts.ofCard(cardGroups).any { learningContexts.schedulesByConcept(it, globalConceptScheduling) }
    }
    var showGroupSelectionDialog by remember { mutableStateOf(false) }
    var showCsvGroupSelectionForExport by remember { mutableStateOf(false) }
    var showExportHistoryDialog by remember { mutableStateOf(false) }
    var exportHistoryPendingGroupIds by remember { mutableStateOf<List<Long>?>(null) } // null = export all
    var includeHistoryInExport by remember { mutableStateOf(false) }

    // Handle back press when in selection mode
    if (isSelectionMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    // Scroll to top when sort option or direction changes, and only then.
    val cardListState = listPosition.list
    ScrollToTopOnSortChange(listPosition, cardSortOption to sortDirection)

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedCardIds.size,
                    onExit = onExitSelectionMode,
                    onSelectAll = onSelectAllCards,
                    actions = listOf(
                        SelectionAction("Change groups", AppIcons.CardGroups) { showBulkGroupDialog = true },
                        if (schedulesByConcept(selectedCardIds)) {
                            SelectionAction("Reset (concept scheduling on)", Icons.Default.Refresh, enabled = false) {}
                        } else {
                            SelectionAction("Reset state", Icons.Default.Refresh) { showBulkResetDialog = true }
                        },
                        SelectionAction("Disable", Icons.Default.Block) { onSetSelectedCardsDisabled(true) },
                        SelectionAction("Enable", Icons.Default.PlayArrow) { onSetSelectedCardsDisabled(false) },
                        SelectionAction("Delete", Icons.Default.Delete, destructive = true) { showBulkDeleteDialog = true }
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
                                        Icon(AppIcons.CardGroups, contentDescription = null)
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
                                        Icon(AppIcons.CardGroups, contentDescription = null)
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
                        selected = selectedGroupFilters.isEmpty() && selectedConceptFilters.isEmpty() && !showDisabledFilter,
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

            // Concept filter chips: a card carrying any selected concept is shown.
            if (concepts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(concepts, key = { it.id }) { concept ->
                        FilterChip(
                            selected = concept.id in selectedConceptFilters,
                            onClick = { onToggleConceptFilter(concept.id) },
                            label = { Text(concept.name) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
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
                                selectedConceptFilters.isNotEmpty() -> "No cards with the selected concepts"
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
                            concepts = conceptsByCard[card.id].orEmpty(),
                            learningStates = learningStates,
                            learningGroups = learningGroups,
                            isSelectionMode = isSelectionMode,
                            isSelected = card.id in selectedCardIds,
                            sortOption = cardSortOption,
                            onEdit = { onNavigateToEditCard(card) },
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


    // Import/Export state dialogs
    ImportExportDialogs(importExportState, groups, onResetImportExportState, onCsvImportInto, onCsvImportIntoNewGroup)

    // Bulk delete confirmation dialog
    if (showBulkDeleteDialog) {
        ConfirmDeleteDialog(
            title = "Delete ${selectedCardIds.size} Cards?",
            text = "Are you sure you want to delete these cards? This action cannot be undone.",
            onConfirm = { onDeleteSelectedCards(); showBulkDeleteDialog = false },
            onDismiss = { showBulkDeleteDialog = false }
        )
    }

    // Bulk reset state confirmation dialog
    if (showBulkResetDialog) {
        BulkResetDialog(
            selectedCardIds = selectedCardIds,
            allCardsWithGroups = allCardsWithGroups,
            learningGroups = learningGroups,
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
            initiallySelected = selectedGroupFilters,
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
            initiallySelected = selectedGroupFilters,
            onDismiss = { showCsvGroupSelectionForExport = false },
            onConfirm = { selectedGroupIds ->
                showCsvGroupSelectionForExport = false
                onExportGroupsCsv(selectedGroupIds)
            }
        )
    }
}
