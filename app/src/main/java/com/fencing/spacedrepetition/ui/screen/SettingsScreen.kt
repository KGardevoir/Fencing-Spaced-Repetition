// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.BuildConfig
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.SupportLinks
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.ui.components.FsrsRetentionPreview
import com.fencing.spacedrepetition.ui.components.RetentionSelector
import com.fencing.spacedrepetition.util.formatDateAtTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    totalCards: Int,
    themeMode: ThemeMode,
    autoShowAnswer: Boolean,
    cardsPerSession: Int,
    randomizeDueCards: Boolean,
    randomizeBucketHours: Int,
    maximumInterval: Int,
    practiceDays: Set<Int>,
    fsrsRetention: Int,
    practiceScheduleEstimate: ScheduleEstimate?,
    historyWindowDays: Int,
    sm2IntervalModifier: Int,
    fsrsEnableFuzzing: Boolean,
    autoBackupEnabled: Boolean,
    autoBackupIntervalDays: Int,
    lastBackupTime: Long,
    maxBackupsKept: Int,
    // Where backups go. The screen only ever shows this name and asks for a
    // new folder; picking one is the platform's business, not the screen's.
    backupFolderName: String?,
    onPickBackupFolder: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAutoShowAnswer: (Boolean) -> Unit,
    onSetCardsPerSession: (Int) -> Unit,
    onSetRandomizeDueCards: (Boolean) -> Unit,
    onSetRandomizeBucketHours: (Int) -> Unit,
    onSetMaximumInterval: (Int) -> Unit,
    onTogglePracticeDay: (Int) -> Unit,
    onSetFsrsRetention: (Int) -> Unit,
    onSetHistoryWindowDays: (Int) -> Unit,
    onSetSm2IntervalModifier: (Int) -> Unit,
    onSetFsrsEnableFuzzing: (Boolean) -> Unit,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetAutoBackupIntervalDays: (Int) -> Unit,
    onSetMaxBackupsKept: (Int) -> Unit,
    onRunBackupNow: () -> Unit,
    onDeleteAllCards: () -> Unit,
    onOpenLink: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val bucketPresets = SettingsConstants.BUCKET_PRESETS
    val currentBucketIndex = SettingsConstants.findPresetIndex(bucketPresets, randomizeBucketHours)

    val intervalPresets = SettingsConstants.INTERVAL_PRESETS
    val currentPresetIndex = SettingsConstants.findPresetIndex(intervalPresets, maximumInterval)

    val sm2ModifierPresets = SettingsConstants.SM2_MODIFIER_PRESETS
    val currentSm2ModifierIndex = SettingsConstants.findPresetIndex(sm2ModifierPresets, sm2IntervalModifier)

    val backupIntervalPresets = SettingsConstants.BACKUP_INTERVAL_PRESETS
    val currentBackupIntervalIndex = SettingsConstants.findPresetIndex(backupIntervalPresets, autoBackupIntervalDays)

    val maxBackupsKeptPresets = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS
    val currentMaxBackupsKeptIndex = SettingsConstants.findPresetIndex(maxBackupsKeptPresets, maxBackupsKept)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        .clickable { onSetThemeMode(mode) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onSetThemeMode(mode) }
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
            HorizontalDivider()
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
                    onValueChange = { onSetCardsPerSession(it.roundToInt()) },
                    valueRange = SettingsConstants.CARDS_PER_SESSION_MIN..SettingsConstants.CARDS_PER_SESSION_MAX,
                    steps = SettingsConstants.CARDS_PER_SESSION_STEPS,
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
                    .clickable { onSetAutoShowAnswer(!autoShowAnswer) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-show Description",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Show description immediately when viewing cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoShowAnswer,
                    onCheckedChange = onSetAutoShowAnswer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Randomize due cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetRandomizeDueCards(!randomizeDueCards) }
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
                    onCheckedChange = onSetRandomizeDueCards
                )
            }

            // Randomization bucket size (only shown when randomization is enabled)
            if (randomizeDueCards) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sampling Bucket Size",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = bucketPresets[currentBucketIndex].second,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentBucketIndex.toFloat(),
                        onValueChange = { newIndex ->
                            val presetValue = bucketPresets[newIndex.roundToInt()].first
                            onSetRandomizeBucketHours(presetValue)
                        },
                        valueRange = 0f..(bucketPresets.size - 1).toFloat(),
                        steps = bucketPresets.size - 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Cards due within the same bucket are shuffled randomly. Smaller buckets preserve due-date ordering more strictly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Scheduling section
            Text(
                "Scheduling",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Practice days
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Practice Days",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SettingsConstants.DAY_LABELS.forEach { (day, label) ->
                        val selected = practiceDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = { onTogglePracticeDay(day) },
                            label = { Text(label) },
                            modifier = Modifier.size(width = 42.dp, height = 36.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (practiceDays.size == 7) "Daily" else "${practiceDays.size} days per week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cards are scheduled to come due on days you practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Algorithm section
            Text(
                "Algorithm (FSRS-6 / SM-2)",
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
                        val presetValue = intervalPresets[newIndex.roundToInt()].first
                        onSetMaximumInterval(presetValue)
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

            Spacer(modifier = Modifier.height(8.dp))

            // FSRS desired retention
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "FSRS Desired Retention",
                    style = MaterialTheme.typography.bodyLarge
                )
                RetentionSelector(
                    retentionPercent = fsrsRetention,
                    onRetentionChange = onSetFsrsRetention,
                    cardsInRotation = totalCards,
                    scheduleDaysPerWeek = practiceDays.size,
                    scheduleSetsPerPractice = cardsPerSession,
                    historyEstimate = practiceScheduleEstimate,
                    historyWindowDays = historyWindowDays,
                    onHistoryWindowDaysChange = onSetHistoryWindowDays
                )
                Text(
                    text = "Target probability of remembering a card when it comes due (FSRS only).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FsrsRetentionPreview(retentionPercent = fsrsRetention)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FSRS interval fuzzing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetFsrsEnableFuzzing(!fsrsEnableFuzzing) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FSRS Interval Fuzzing",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Add a small random variance (\u22645\u00a0%) to FSRS intervals to spread reviews and prevent pile-ups (FSRS only).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fsrsEnableFuzzing,
                    onCheckedChange = onSetFsrsEnableFuzzing
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SM-2 interval modifier
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
                        text = "SM-2 Interval Modifier",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = sm2ModifierPresets[currentSm2ModifierIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentSm2ModifierIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = sm2ModifierPresets[newIndex.roundToInt()].first
                        onSetSm2IntervalModifier(presetValue)
                    },
                    valueRange = 0f..(sm2ModifierPresets.size - 1).toFloat(),
                    steps = sm2ModifierPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Scales all SM-2 review intervals (SM-2 only). " +
                        "100\u00a0% = default behaviour. " +
                        "Lower values (e.g.\u00a075\u00a0%) mean shorter intervals and more reviews; " +
                        "higher values (e.g.\u00a0150\u00a0%) mean longer intervals and fewer reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Backup section
            Text(
                "Automatic Backup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetAutoBackupEnabled(!autoBackupEnabled) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Back Up Automatically",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Periodically save a compressed copy of your cards, groups, and history to a folder you choose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = onSetAutoBackupEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onPickBackupFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (backupFolderName != null) "Change Backup Folder" else "Choose Backup Folder")
            }
            if (backupFolderName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Folder: $backupFolderName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Backup frequency
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
                        text = "Backup Frequency",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = backupIntervalPresets[currentBackupIntervalIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentBackupIntervalIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = backupIntervalPresets[newIndex.roundToInt()].first
                        onSetAutoBackupIntervalDays(presetValue)
                    },
                    valueRange = 0f..(backupIntervalPresets.size - 1).toFloat(),
                    steps = backupIntervalPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Max backups kept
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
                        text = "Backups to Keep",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = maxBackupsKeptPresets[currentMaxBackupsKeptIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = currentMaxBackupsKeptIndex.toFloat(),
                    onValueChange = { newIndex ->
                        val presetValue = maxBackupsKeptPresets[newIndex.roundToInt()].first
                        onSetMaxBackupsKept(presetValue)
                    },
                    valueRange = 0f..(maxBackupsKeptPresets.size - 1).toFloat(),
                    steps = maxBackupsKeptPresets.size - 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Older backups beyond this count are automatically deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (lastBackupTime > 0) {
                    "Last backup: " + formatDateAtTime(lastBackupTime)
                } else {
                    "No backup has been made yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRunBackupNow,
                modifier = Modifier.fillMaxWidth(),
                enabled = backupFolderName != null
            ) {
                Text("Back Up Now")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Support section
            Text(
                "Support Development",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This app is free software and always will be. If you find it useful, you can support continued development below. Links open in your browser.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            SupportLinks.links.forEach { link ->
                ElevatedButton(
                    onClick = {
                        onOpenLink(link.url)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(link.label)
                        Text(
                            link.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // About section
            Text(
                "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Build", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            BuildConfig.BUILD_TYPE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Commit", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            BuildConfig.GIT_COMMIT,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Danger Zone section
            Text(
                "Danger Zone",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showDeleteAllDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = totalCards > 0
            ) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Delete All Cards",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permanently remove all cards, review history, and group assignments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Delete All Cards confirmation dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete All Cards?") },
            text = {
                Text(
                    "This will permanently delete all $totalCards cards, " +
                        "their review history, and all group assignments. " +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllCards()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
