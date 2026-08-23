// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToOpponents: () -> Unit
) {
    val dueCardCount by cardViewModel.dueCardCount.collectAsState()
    val totalCards by cardViewModel.cardCount.collectAsState()
    val groups by groupViewModel.allGroups.collectAsState()
    val globalCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val savedGroupId by settingsViewModel.selectedGroupId.collectAsState()

    var showGroupSelectionDialog by remember { mutableStateOf(false) }

    // Derive selected group from persisted ID and groups list
    val selectedGroup = groups.find { it.id == savedGroupId } ?: groups.firstOrNull()

    // Auto-select first group when groups become available and no valid selection exists
    LaunchedEffect(groups, savedGroupId) {
        if (groups.isNotEmpty()) {
            val currentGroupExists = groups.any { it.id == savedGroupId }
            if (!currentGroupExists) {
                // Save the first group's ID if no valid selection
                settingsViewModel.setSelectedGroupId(groups.first().id)
            }
        }
    }

    // Resolve cardsPerSession: group override takes precedence over global
    val cardsPerSession = selectedGroup?.cardsPerSession ?: globalCardsPerSession

    // Calculate total cards for selected group (allow additional studying)
    val cardsForSelectedGroup = selectedGroup?.let { group ->
        cardViewModel.getCardCountByGroup(group.id).collectAsState(initial = 0).value
    } ?: totalCards

    // Still track due cards for display
    val dueForSelectedGroup = selectedGroup?.let { group ->
        cardViewModel.getDueCardCountByGroup(group.id).collectAsState(initial = 0).value
    } ?: dueCardCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AnkiFlex") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToOpponents) {
                        Icon(Icons.Default.Person, "Manage Opponents")
                    }
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // App title card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sports,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "AnkiFlex",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Practice $cardsPerSession ${if (cardsPerSession == 1) "card" else "cards"}, grade at the end",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
                                text = "Practice Group",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedGroup?.name ?: "Select a group",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "$dueForSelectedGroup due",
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

            // Main actions
            Button(
                onClick = {
                    practiceViewModel.startNewSession(cardsPerSession, selectedGroup?.id)
                    onNavigateToPractice()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled = cardsForSelectedGroup >= 1
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Start Practice",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (cardsForSelectedGroup < 1) {
                Text(
                    text = if (totalCards == 0) "Add some cards to get started"
                    else if (selectedGroup != null) "No cards in ${selectedGroup.name}"
                    else "No cards available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToCards,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Cards")
                }
                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "History")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }


    // Group selection dialog
    if (showGroupSelectionDialog) {
        GroupSelectionDialog(
            groups = groups,
            selectedGroup = selectedGroup,
            onSelect = { group ->
                settingsViewModel.setSelectedGroupId(group.id)
                showGroupSelectionDialog = false
            },
            onDismiss = { showGroupSelectionDialog = false }
        )
    }

}

@Composable
fun GroupSelectionDialog(
    groups: List<Group>,
    selectedGroup: Group?,
    onSelect: (Group) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        title = { Text("Select Practice Group") },
        text = {
            LazyColumn {
                items(groups) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(group) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedGroup?.id == group.id,
                            onClick = { onSelect(group) }
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
