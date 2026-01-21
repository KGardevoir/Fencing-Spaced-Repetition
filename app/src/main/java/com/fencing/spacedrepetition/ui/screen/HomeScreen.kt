package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.preferences.ThemeMode
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
    onNavigateToGroups: () -> Unit
) {
    val dueCardCount by cardViewModel.dueCardCount.collectAsState()
    val totalCards by cardViewModel.cardCount.collectAsState()
    val groups by groupViewModel.allGroups.collectAsState()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val cardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val savedGroupId by settingsViewModel.selectedGroupId.collectAsState()

    var showGroupSelectionDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

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
                title = { Text("Martial Arts Practice") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToGroups) {
                        Icon(Icons.Default.Folder, "Manage Groups")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
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
                    icon = Icons.Default.LibraryBooks,
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
                Spacer(modifier = Modifier.height(16.dp))


                // Main actions
                Button(
                    onClick = {
                        practiceViewModel.startNewSession(cardsPerSession, selectedGroup?.id)
                        onNavigateToPractice()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    enabled = cardsForSelectedGroup >= 1
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

                if (cardsForSelectedGroup < 1) {
                    Text(
                        text = if (totalCards == 0) "Add some cards to get started"
                        else if (selectedGroup != null) "No cards in ${selectedGroup.name}"
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
                        Icons.Default.LibraryBooks,
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
            selectedGroup = selectedGroup,
            onSelect = { group ->
                settingsViewModel.setSelectedGroupId(group.id)
                showGroupSelectionDialog = false
            },
            onDismiss = { showGroupSelectionDialog = false }
        )
    }

    // Settings dialog
    if (showSettingsDialog) {
        val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
        val randomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
        val maximumInterval by settingsViewModel.maximumInterval.collectAsState()
        SettingsDialog(
            currentThemeMode = themeMode,
            autoShowAnswer = autoShowAnswer,
            cardsPerSession = cardsPerSession,
            randomizeDueCards = randomizeDueCards,
            maximumInterval = maximumInterval,
            onThemeModeChange = { settingsViewModel.setThemeMode(it) },
            onAutoShowAnswerChange = { settingsViewModel.setAutoShowAnswer(it) },
            onCardsPerSessionChange = { settingsViewModel.setCardsPerSession(it) },
            onRandomizeDueCardsChange = { settingsViewModel.setRandomizeDueCards(it) },
            onMaximumIntervalChange = { settingsViewModel.setMaximumInterval(it) },
            onDismiss = { showSettingsDialog = false }
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

@Composable
fun SettingsDialog(
    currentThemeMode: ThemeMode,
    autoShowAnswer: Boolean,
    cardsPerSession: Int,
    randomizeDueCards: Boolean,
    maximumInterval: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAutoShowAnswerChange: (Boolean) -> Unit,
    onCardsPerSessionChange: (Int) -> Unit,
    onRandomizeDueCardsChange: (Boolean) -> Unit,
    onMaximumIntervalChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        title = { Text("Settings") },
        text = {
            Column {
                // Theme section
                Text(
                    "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == mode,
                            onClick = { onThemeModeChange(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "System Default"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "Follow device settings"
                                    ThemeMode.LIGHT -> "Always use light theme"
                                    ThemeMode.DARK -> "Always use dark theme"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Practice section
                Text(
                    "Practice",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Cards per session
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cards per Session",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$cardsPerSession",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = cardsPerSession.toFloat(),
                        onValueChange = { onCardsPerSessionChange(it.toInt()) },
                        valueRange = 1f..6f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Number of cards to practice each session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-show answer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAutoShowAnswerChange(!autoShowAnswer) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-show Answer",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Show answer immediately when viewing cards",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoShowAnswer,
                        onCheckedChange = onAutoShowAnswerChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Randomize due cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRandomizeDueCardsChange(!randomizeDueCards) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Randomize Due Cards",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Shuffle cards with similar due dates for variety",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = randomizeDueCards,
                        onCheckedChange = onRandomizeDueCardsChange
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Algorithm section
                Text(
                    "Algorithm",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Maximum interval
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Maximum Interval",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (maximumInterval >= 365) {
                                "${maximumInterval / 365} ${if (maximumInterval / 365 == 1) "year" else "years"}"
                            } else {
                                "$maximumInterval days"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = maximumInterval.toFloat(),
                        onValueChange = { onMaximumIntervalChange(it.toInt()) },
                        valueRange = 30f..36500f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Maximum days between reviews (30 days to 100 years)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
