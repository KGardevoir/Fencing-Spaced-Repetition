package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cardViewModel: CardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: GroupViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToPractice: () -> Unit,
    onNavigateToCards: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val dueCardCount by cardViewModel.dueCardCount.collectAsState()
    val totalCards by cardViewModel.cardCount.collectAsState()
    val groups by groupViewModel.allGroups.collectAsState()
    val globalCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val savedGroupIds by settingsViewModel.selectedGroupIds.collectAsState()

    var showGroupSelectionDialog by remember { mutableStateOf(false) }

    // Filter to only valid group IDs
    val selectedGroupIds = savedGroupIds.filter { id -> groups.any { it.id == id } }.toSet()
    val selectedGroups = groups.filter { it.id in selectedGroupIds }

    // Auto-select first group when groups become available and no valid selection exists
    LaunchedEffect(groups, savedGroupIds) {
        if (groups.isNotEmpty() && selectedGroupIds.isEmpty()) {
            settingsViewModel.setSelectedGroupIds(setOf(groups.first().id))
        }
    }

    // Resolve cardsPerSession: single group override takes precedence, otherwise global
    val cardsPerSession = if (selectedGroups.size == 1) {
        selectedGroups.first().cardsPerSession ?: globalCardsPerSession
    } else {
        globalCardsPerSession
    }

    // Calculate total cards for selected groups
    val selectedGroupIdList = selectedGroupIds.toList()
    val cardsForSelectedGroups = if (selectedGroupIdList.isNotEmpty()) {
        cardViewModel.getCardCountByGroups(selectedGroupIdList).collectAsState(initial = 0).value
    } else {
        totalCards
    }

    // Track due cards for display
    val dueForSelectedGroups = if (selectedGroupIdList.isNotEmpty()) {
        cardViewModel.getDueCardCountByGroups(selectedGroupIdList).collectAsState(initial = 0).value
    } else {
        dueCardCount
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Martial Arts Practice") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToGroups) {
                        Icon(Icons.Default.Folder, "Manage Groups")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // App title card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp)
                ){
                    Column(
                        modifier = Modifier.weight(2f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Delayed Choice\nSpaced Repetition",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Practice $cardsPerSession ${if (cardsPerSession == 1) "card" else "cards"}, grade at the end",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ){
                        Icon(
                            imageVector = Icons.Default.Sports,
                            contentDescription = null,
                            modifier = Modifier.size(128.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Schedule,
                    value = dueCardCount.toString(),
                    label = "Due Now",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondaryContainer
                )

                StatCard(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    value = totalCards.toString(),
                    label = "Total Cards",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                )
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ){
                Spacer(modifier = Modifier.weight(1f))

                // Group selection card
                if (groups.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGroupSelectionDialog = true }
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
                                Text(
                                    text = if (selectedGroups.size == 1) "Practice Group" else "Practice Groups",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = when {
                                        selectedGroups.isEmpty() -> "Select groups"
                                        selectedGroups.size == 1 -> selectedGroups.first().name
                                        selectedGroups.size <= 3 -> selectedGroups.joinToString(", ") { it.name }
                                        else -> "${selectedGroups.size} groups selected"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "$dueForSelectedGroups due",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))


                // Main actions
                Button(
                    onClick = {
                        practiceViewModel.startNewSession(cardsPerSession, selectedGroupIdList)
                        onNavigateToPractice()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    enabled = cardsForSelectedGroups >= 1
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Start Practice",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                if (cardsForSelectedGroups < 1) {
                    Text(
                        text = if (totalCards == 0) "Add some cards to get started"
                        else if (selectedGroups.isNotEmpty()) "No cards in selected groups"
                        else "No cards available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Add spacing when helper text is not shown
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = onNavigateToCards,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manage Cards",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }


    // Group selection dialog
    if (showGroupSelectionDialog) {
        GroupSelectionDialog(
            groups = groups,
            selectedGroupIds = selectedGroupIds,
            onToggleGroup = { groupId ->
                settingsViewModel.toggleGroupSelection(groupId)
            },
            onSelectAll = {
                settingsViewModel.setSelectedGroupIds(groups.map { it.id }.toSet())
            },
            onClearAll = {
                settingsViewModel.setSelectedGroupIds(emptySet())
            },
            onDismiss = { showGroupSelectionDialog = false }
        )
    }
}

@Composable
fun GroupSelectionDialog(
    groups: List<Group>,
    selectedGroupIds: Set<Long>,
    onToggleGroup: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        title = { Text("Select Practice Groups") },
        text = {
            Column {
                // Select All / Clear All row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSelectAll) {
                        Text("Select All")
                    }
                    TextButton(
                        onClick = onClearAll,
                        enabled = selectedGroupIds.isNotEmpty()
                    ) {
                        Text("Clear")
                    }
                }
                LazyColumn {
                    items(groups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleGroup(group.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = group.id in selectedGroupIds,
                                onCheckedChange = { onToggleGroup(group.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(group.name, style = MaterialTheme.typography.bodyLarge)
                                if (group.description.isNotEmpty()) {
                                    Text(
                                        group.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

