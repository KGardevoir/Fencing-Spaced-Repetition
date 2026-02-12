package com.fencing.spacedrepetition.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.viewmodel.CardSortOption
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CardListScreen(
    viewModel: CardViewModel,
    groupViewModel: GroupViewModel,
    onNavigateToAddCard: (Long?) -> Unit,
    onNavigateToEditCard: (Card) -> Unit,
    onNavigateBack: () -> Unit
) {
    val allCards by viewModel.filteredCards.collectAsState()
    val allCardsWithGroups by viewModel.allCardsWithGroups.collectAsState()
    val groups by groupViewModel.allGroups.collectAsState()
    val selectedGroupFilter by viewModel.selectedGroupFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cardCount by viewModel.cardCount.collectAsState()
    val importExportState by viewModel.importExportState.collectAsState()
    val cardSortOption by viewModel.cardSortOption.collectAsState()
    val context = LocalContext.current

    // Selection mode state
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedCardIds by viewModel.selectedCardIds.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<Card?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkGroupDialog by remember { mutableStateOf(false) }
    var showBulkResetDialog by remember { mutableStateOf(false) }
    var showGroupSelectionDialog by remember { mutableStateOf(false) }
    var selectedGroupsForExport by remember { mutableStateOf<List<Long>>(emptyList()) }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importCards(uri, context.contentResolver)
        }
    }

    // File picker for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportAllCards(uri, context.contentResolver)
        }
    }

    // File picker for export selected groups
    val exportSelectedGroupsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.exportSelectedGroups(selectedGroupsForExport, uri, context.contentResolver)
        }
    }

    // Handle back press when in selection mode
    if (isSelectionMode) {
        BackHandler { viewModel.exitSelectionMode() }
    }

    // Scroll to top when sort option changes
    val cardListState = rememberLazyListState()
    LaunchedEffect(cardSortOption) {
        cardListState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = { Text("${selectedCardIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, "Exit selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAllCards() }) {
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
                                            viewModel.setCardSortOption(option)
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
                                        viewModel.toggleSelectionMode()
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
                                        importLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "text/plain", "text/tab-separated-values", "application/octet-stream", "*/*"))
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.FileUpload, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export All Cards") },
                                    onClick = {
                                        showMenu = false
                                        exportLauncher.launch("all_cards.tsv.gz")
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
                    onClick = { onNavigateToAddCard(selectedGroupFilter?.id) },
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
                onValueChange = { viewModel.updateSearchQuery(it) },
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
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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

            // Group filter
            if (groups.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedGroupFilter == null,
                            onClick = { viewModel.selectGroupFilter(null) },
                            label = { Text("All") }
                        )
                    }
                    items(groups) { group ->
                        FilterChip(
                            selected = selectedGroupFilter?.id == group.id,
                            onClick = { viewModel.selectGroupFilter(group) },
                            label = { Text(group.name) }
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
                                selectedGroupFilter != null -> "No cards in this group"
                                else -> "No cards yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Try a different search term"
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
                        val learningStates by viewModel.getLearningStatesForCard(card.id)
                            .collectAsState(initial = emptyList())

                        CardListItem(
                            card = card,
                            groups = cardGroups,
                            learningStates = learningStates,
                            isSelectionMode = isSelectionMode,
                            isSelected = card.id in selectedCardIds,
                            onEdit = { onNavigateToEditCard(card) },
                            onDelete = { showDeleteDialog = card },
                            onToggleSelection = { viewModel.toggleCardSelection(card.id) },
                            onLongPress = {
                                if (!isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                    viewModel.toggleCardSelection(card.id)
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
                        viewModel.deleteCard(card)
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
                onDismissRequest = { viewModel.resetImportExportState() },
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
                    Button(onClick = { viewModel.resetImportExportState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetImportExportState() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} cards.") },
                confirmButton = {
                    Button(onClick = { viewModel.resetImportExportState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetImportExportState() },
                icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.resetImportExportState() }) {
                        Text("OK")
                    }
                }
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
                        viewModel.deleteSelectedCards()
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
            currentGroupFilter = selectedGroupFilter,
            onDismiss = { showBulkResetDialog = false },
            onResetGlobal = {
                viewModel.resetSelectedCardsGlobalState()
                showBulkResetDialog = false
            },
            onResetGroups = { groupIds ->
                viewModel.resetSelectedCardsInGroups(groupIds)
                showBulkResetDialog = false
            },
            onResetBoth = { groupIds ->
                viewModel.resetSelectedCardsBothStates(groupIds)
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
                viewModel.updateSelectedCardsGroups(selectedGroupIds)
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
                selectedGroupsForExport = selectedGroupIds
                showGroupSelectionDialog = false
                exportSelectedGroupsLauncher.launch("selected_groups_cards.tsv.gz")
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
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
                            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            dueDateText = formatter.format(Date(card.nextReview))
                            val now = System.currentTimeMillis()
                            val diff = card.nextReview - now
                            val daysDiff = (diff / (1000 * 60 * 60 * 24)).toInt()
                            dueDateColor = when {
                                diff <= 0 -> MaterialTheme.colorScheme.error
                                daysDiff == 0 -> MaterialTheme.colorScheme.tertiary
                                daysDiff <= 3 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                        Text(
                            text = dueDateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = dueDateColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
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
                Text(
                    text = card.answer,
                    style = MaterialTheme.typography.bodyMedium
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

                    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    independentGroups.forEach { group ->
                        val state = learningStates.find { it.groupId == group.id }
                        val stateText = if (state == null) {
                            "${group.name}: No state"
                        } else {
                            val nextReview = if (state.nextReview == 0L) {
                                "New"
                            } else {
                                formatter.format(Date(state.nextReview))
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
                    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    "Next: ${formatter.format(Date(card.nextReview))}"
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
    allCardsWithGroups: List<com.fencing.spacedrepetition.data.model.CardWithGroups>,
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
