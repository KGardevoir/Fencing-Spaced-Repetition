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
import com.fencing.spacedrepetition.util.BackupReminder

/**
 * The home screen.
 *
 * Takes values and callbacks rather than view models, like GroupEditScreen
 * does. That is what lets it live in shared code -- the view models it used to
 * take are AndroidViewModels, holding a Context for import and export, and
 * nothing holding a Context can follow this screen into a browser.
 *
 * It also leaves the screen easier to reason about. Everything it used to
 * derive -- which group is selected, which card counts apply to it, whether
 * to persist a fallback selection -- was state management rather than
 * presentation, and it now happens where the view models are.
 *
 * Both a whole-collection and a per-group count are needed, and they are not
 * interchangeable: the two stat cards at the top report the collection, while
 * the practice row reports the selected group.
 *
 * @param totalCardCount every card, for the stat card and the empty state.
 * @param totalDueCount every due card, for the stat card.
 * @param cardsToPractise cards available in [selectedGroup], or in the whole
 *   collection when no group is selected. Gates the practice button.
 * @param dueCount due cards on the same basis, shown next to the group name.
 * @param onStartPractice begins a session and navigates to it -- one callback
 *   because the button always did both.
 * @param backupReminder the nudge to show above everything else, or null for
 *   silence. Null on Android, always: it backs up on its own, and this is
 *   what the browser has instead.
 * @param onBackUpNow writes a backup, which in a browser means downloading
 *   one. The reminder goes away when the backup is recorded.
 * @param onDismissBackupReminder puts the reminder away until it comes due
 *   again. Turning it off for good is a switch in the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    groups: List<Group>,
    selectedGroup: Group?,
    cardsPerSession: Int,
    totalCardCount: Int,
    totalDueCount: Int,
    cardsToPractise: Int,
    dueCount: Int,
    backupReminder: BackupReminder?,
    onBackUpNow: () -> Unit,
    onDismissBackupReminder: () -> Unit,
    onSelectGroup: (Group) -> Unit,
    onStartPractice: () -> Unit,
    onNavigateToCards: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToOpponents: () -> Unit
) {
    var showGroupSelectionDialog by remember { mutableStateOf(false) }

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

            if (backupReminder != null) {
                BackupReminderCard(
                    reminder = backupReminder,
                    onBackUpNow = onBackUpNow,
                    onDismiss = onDismissBackupReminder,
                    onOpenSettings = onNavigateToSettings
                )
            }

            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.Schedule,
                    value = totalDueCount.toString(),
                    label = "Due Now",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondaryContainer
                )

                StatCard(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    value = totalCardCount.toString(),
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
                            text = "$dueCount due",
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
                onClick = onStartPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled = cardsToPractise >= 1
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

            if (cardsToPractise < 1) {
                Text(
                    text = if (totalCardCount == 0) "Add some cards to get started"
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
                onSelectGroup(group)
                showGroupSelectionDialog = false
            },
            onDismiss = { showGroupSelectionDialog = false }
        )
    }

}

/**
 * The nudge to back up, on the screen the user starts from.
 *
 * In the home screen rather than a notification, because a browser tab that
 * is closed cannot notify anyone -- the reminder has to be somewhere they
 * already look, and this is the first thing they see.
 *
 * It offers the backup itself rather than only saying it is due: a reminder
 * whose remedy is three taps away in another screen is a reminder people
 * learn to scroll past.
 *
 * Dismissing puts it away until the interval comes round again, rather than
 * for good -- "not now" is the thing people actually mean, and the switch in
 * Settings, which the card links to, is there for when they mean never.
 */
@Composable
private fun BackupReminderCard(
    reminder: BackupReminder,
    onBackUpNow: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Time to back up",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = when (val days = reminder.daysSinceBackup) {
                            null -> "Your cards have never been backed up. They live in this " +
                                "browser's storage, which it is free to clear."
                            1 -> "Your last backup was a day ago."
                            else -> "Your last backup was $days days ago."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss until it is due again",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBackUpNow,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Download a Backup")
                }
                TextButton(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }
        }
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
