// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.BuildInfo
import com.fencing.spacedrepetition.SupportLinks
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.util.formatDateAtTime
import com.fencing.spacedrepetition.ui.viewmodel.AppSettings
import kotlin.math.roundToInt

/**
 * Material's own opacity for disabled content, which its controls apply for
 * themselves and its Text does not. Used to dim the labels beside a control
 * that has been switched off by the platform rather than by the user.
 */
private const val DISABLED_ALPHA = 0.38f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    buildInfo: BuildInfo,
    settings: AppSettings,
    /**
     * Whether this platform can back up on its own.
     *
     * False in a browser, where nothing wakes the page while it is closed.
     * The automatic-backup controls are then shown greyed out rather than
     * hidden -- the feature exists, this build cannot run it -- and the
     * reminder below them takes over the job.
     */
    automaticBackupAvailable: Boolean,
    // Where backups go. The screen only ever shows this name and asks for a
    // new folder; picking one is the platform's business, not the screen's.
    backupFolderName: String?,
    onPickBackupFolder: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAutoShowAnswer: (Boolean) -> Unit,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetAutoBackupIntervalDays: (Int) -> Unit,
    onSetMaxBackupsKept: (Int) -> Unit,
    onSetBackupReminderEnabled: (Boolean) -> Unit,
    onSetBackupReminderIntervalDays: (Int) -> Unit,
    onRunBackupNow: () -> Unit,
    onOpenLink: (String) -> Unit,
    onNavigateBack: () -> Unit
) {



    val backupIntervalPresets = SettingsConstants.BACKUP_INTERVAL_PRESETS
    val currentBackupIntervalIndex = SettingsConstants.findPresetIndex(backupIntervalPresets, settings.autoBackupIntervalDays)

    val maxBackupsKeptPresets = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS
    val currentMaxBackupsKeptIndex = SettingsConstants.findPresetIndex(maxBackupsKeptPresets, settings.maxBackupsKept)

    val reminderIntervalPresets = SettingsConstants.BACKUP_REMINDER_INTERVAL_PRESETS
    val currentReminderIntervalIndex =
        SettingsConstants.findPresetIndex(reminderIntervalPresets, settings.backupReminderIntervalDays)

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
                        selected = settings.themeMode == mode,
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

            // Auto-show answer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSetAutoShowAnswer(!settings.autoShowAnswer) }
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
                    checked = settings.autoShowAnswer,
                    onCheckedChange = onSetAutoShowAnswer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Backup section.
            //
            // On a platform that cannot back up while it is closed, the
            // controls below are all still drawn, greyed out, and the
            // reminder further down is what actually runs. Drawn rather than
            // hidden because "your phone can do this" is worth knowing, and
            // because a section that changes shape between builds is harder
            // to explain than one that says which half is off.
            val autoBackupTitleColor = if (automaticBackupAvailable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            }
            val autoBackupDetailColor = if (automaticBackupAvailable) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
            }

            Text(
                "Automatic Backup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (automaticBackupAvailable) {
                            Modifier.clickable { onSetAutoBackupEnabled(!settings.autoBackupEnabled) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Back Up Automatically",
                        style = MaterialTheme.typography.bodyLarge,
                        color = autoBackupTitleColor
                    )
                    Text(
                        text = if (automaticBackupAvailable) {
                            "Periodically save a compressed copy of your cards, groups, and history to a folder you choose."
                        } else {
                            "Not available in the browser: nothing wakes the page while it is closed, " +
                                "so a backup can only be one you ask for. The reminder below asks you to."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = autoBackupDetailColor
                    )
                }
                Switch(
                    // Off, not merely disabled, where nothing is scheduled: a
                    // switch left showing on would be claiming a backup is
                    // being taken.
                    checked = settings.autoBackupEnabled && automaticBackupAvailable,
                    onCheckedChange = onSetAutoBackupEnabled,
                    enabled = automaticBackupAvailable
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onPickBackupFolder,
                modifier = Modifier.fillMaxWidth(),
                enabled = automaticBackupAvailable
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
                    color = autoBackupDetailColor
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
                        style = MaterialTheme.typography.bodyLarge,
                        color = autoBackupTitleColor
                    )
                    Text(
                        text = backupIntervalPresets[currentBackupIntervalIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (automaticBackupAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA)
                        }
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
                    enabled = automaticBackupAvailable,
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
                        style = MaterialTheme.typography.bodyLarge,
                        color = autoBackupTitleColor
                    )
                    Text(
                        text = maxBackupsKeptPresets[currentMaxBackupsKeptIndex].second,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (automaticBackupAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA)
                        }
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
                    enabled = automaticBackupAvailable,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Older backups beyond this count are automatically deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = autoBackupDetailColor
                )
            }

            // The reminder: what a platform without a scheduler offers
            // instead. Same shape as the switch and slider above, because it
            // is the same decision -- how often, and whether at all.
            if (!automaticBackupAvailable) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Backup Reminder",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetBackupReminderEnabled(!settings.backupReminderEnabled) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remind Me to Back Up",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Shows a note on the home screen when a backup is overdue. " +
                                "Your cards live in this browser's storage, which the browser is " +
                                "free to clear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.backupReminderEnabled,
                        onCheckedChange = onSetBackupReminderEnabled
                    )
                }

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
                            text = "Reminder Frequency",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = reminderIntervalPresets[currentReminderIntervalIndex].second,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = currentReminderIntervalIndex.toFloat(),
                        onValueChange = { newIndex ->
                            val presetValue = reminderIntervalPresets[newIndex.roundToInt()].first
                            onSetBackupReminderIntervalDays(presetValue)
                        },
                        valueRange = 0f..(reminderIntervalPresets.size - 1).toFloat(),
                        steps = reminderIntervalPresets.size - 2,
                        enabled = settings.backupReminderEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (settings.lastBackupTime > 0) {
                    "Last backup: " + formatDateAtTime(settings.lastBackupTime)
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
                // A folder is Android's precondition; a browser has none,
                // because the backup is a download rather than a file written
                // somewhere the app chose.
                enabled = !automaticBackupAvailable || backupFolderName != null
            ) {
                Text(if (automaticBackupAvailable) "Back Up Now" else "Download a Backup")
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
                            "${buildInfo.versionName} (${buildInfo.versionCode})",
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
                            buildInfo.buildType,
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
                            buildInfo.gitCommit,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
