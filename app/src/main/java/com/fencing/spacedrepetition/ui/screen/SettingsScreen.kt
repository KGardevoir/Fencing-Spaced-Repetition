package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
    val cardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val randomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
    val maximumInterval by settingsViewModel.maximumInterval.collectAsState()

    // Preset values for maximum interval with better granularity
    val intervalPresets = listOf(
        7 to "1 week",
        14 to "2 weeks",
        30 to "1 month",
        60 to "2 months",
        90 to "3 months",
        180 to "6 months",
        365 to "1 year",
        730 to "2 years",
        1825 to "5 years",
        3650 to "10 years"
    )

    // Find closest preset index to current value
    val currentPresetIndex = intervalPresets.indexOfFirst { it.first >= maximumInterval }
        .let { if (it == -1) intervalPresets.size - 1 else it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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
                        .clickable { settingsViewModel.setThemeMode(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { settingsViewModel.setThemeMode(mode) }
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
                    onValueChange = { settingsViewModel.setCardsPerSession(it.toInt()) },
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
                    .clickable { settingsViewModel.setAutoShowAnswer(!autoShowAnswer) }
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
                    onCheckedChange = { settingsViewModel.setAutoShowAnswer(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Randomize due cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settingsViewModel.setRandomizeDueCards(!randomizeDueCards) }
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
                    onCheckedChange = { settingsViewModel.setRandomizeDueCards(it) }
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
                        text = intervalPresets[currentPresetIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentPresetIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = intervalPresets[newIndex.toInt()].first
                        settingsViewModel.setMaximumInterval(presetValue)
                    },
                    valueRange = 0f..(intervalPresets.size - 1).toFloat(),
                    steps = intervalPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Maximum time between reviews",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
