// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.ui.components.FsrsRetentionPreview
import com.fencing.spacedrepetition.ui.components.RetentionSelector
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GroupEditScreen(
    group: Group,
    globalCardsPerSession: Int,
    globalAutoShowAnswer: Boolean,
    globalRandomizeDueCards: Boolean,
    globalRandomizeBucketHours: Int,
    globalPracticeDays: Set<Int>,
    globalMaximumInterval: Int,
    globalFsrsRetention: Int,
    globalFsrsEnableFuzzing: Boolean,
    groupCardCount: Int,
    practiceScheduleEstimate: ScheduleEstimate?,
    historyWindowDays: Int,
    onHistoryWindowDaysChange: (Int) -> Unit,
    onSave: (Group) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isNewGroup = group.id == 0L

    var name by rememberSaveable { mutableStateOf(group.name) }
    var description by rememberSaveable { mutableStateOf(group.description) }
    var independentLearning by remember { mutableStateOf(group.independentLearning) }

    // Per-group settings overrides (null = use global)
    var overrideCardsPerSession by remember { mutableStateOf(group.cardsPerSession != null) }
    var cardsPerSession by remember { mutableIntStateOf(group.cardsPerSession ?: globalCardsPerSession) }

    var overrideAutoShowAnswer by remember { mutableStateOf(group.autoShowAnswer != null) }
    var autoShowAnswer by remember { mutableStateOf(group.autoShowAnswer ?: globalAutoShowAnswer) }

    var overrideRandomizeDueCards by remember { mutableStateOf(group.randomizeDueCards != null) }
    var randomizeDueCards by remember { mutableStateOf(group.randomizeDueCards ?: globalRandomizeDueCards) }

    var overrideRandomizeBucketHours by remember { mutableStateOf(group.randomizeBucketHours != null) }
    var randomizeBucketHours by remember { mutableIntStateOf(group.randomizeBucketHours ?: globalRandomizeBucketHours) }

    var overridePracticeDays by remember { mutableStateOf(group.practiceDays != null) }
    var practiceDays by remember { mutableStateOf(group.parsePracticeDays() ?: globalPracticeDays) }

    var overrideMaximumInterval by remember { mutableStateOf(group.maximumInterval != null) }
    var maximumInterval by remember { mutableIntStateOf(group.maximumInterval ?: globalMaximumInterval) }

    var overrideFsrsRetention by remember { mutableStateOf(group.fsrsRetention != null) }
    var fsrsRetention by remember { mutableIntStateOf(group.fsrsRetention ?: globalFsrsRetention) }


    var overrideFsrsEnableFuzzing by remember { mutableStateOf(group.fsrsEnableFuzzing != null) }
    var fsrsEnableFuzzing by remember { mutableStateOf(group.fsrsEnableFuzzing ?: globalFsrsEnableFuzzing) }

    val intervalPresets = SettingsConstants.INTERVAL_PRESETS
    val bucketPresets = SettingsConstants.BUCKET_PRESETS

    fun buildUpdatedGroup() = group.copy(
        name = name.trim(),
        description = description.trim(),
        independentLearning = independentLearning,
        cardsPerSession = if (overrideCardsPerSession) cardsPerSession else null,
        autoShowAnswer = if (overrideAutoShowAnswer) autoShowAnswer else null,
        randomizeDueCards = if (overrideRandomizeDueCards) randomizeDueCards else null,
        randomizeBucketHours = if (overrideRandomizeBucketHours) randomizeBucketHours else null,
        practiceDays = if (overridePracticeDays) practiceDays.sorted().joinToString(",") else null,
        maximumInterval = if (overrideMaximumInterval) maximumInterval else null,
        fsrsRetention = if (overrideFsrsRetention) fsrsRetention else null,
        fsrsEnableFuzzing = if (overrideFsrsEnableFuzzing) fsrsEnableFuzzing else null
    )

    val isDirty = buildUpdatedGroup() != group
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

    // Compose Multiplatform's own BackHandler, not the one from
    // androidx.activity: same signature, and a real back gesture on Android.
    // Not the browser's Back button on the web, despite the symmetry -- there
    // Compose raises a back event for the Escape key alone and never touches
    // session history, so this guards the keyboard and the arrow above, and
    // browser Back still leaves the page.
    BackHandler(enabled = isDirty) {
        showUnsavedChangesDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewGroup) "Add Group" else "Edit Group") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) {
                            showUnsavedChangesDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(buildUpdatedGroup()) },
                        enabled = name.isNotBlank()
                    ) {
                        Text(if (isNewGroup) "Add" else "Save")
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
            // Group info section
            Text(
                "Group Info",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { independentLearning = !independentLearning }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Independent Learning", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Cards will have separate learning progress in this group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = independentLearning,
                    onCheckedChange = { independentLearning = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Per-group settings section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Group Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Override global settings for this group. Disabled overrides use the global default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Cards per session override
            SettingOverrideSection(
                label = "Cards per Session",
                overridden = overrideCardsPerSession,
                onOverrideChange = { overrideCardsPerSession = it }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$cardsPerSession", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = cardsPerSession.toFloat(),
                    onValueChange = { cardsPerSession = it.roundToInt() },
                    valueRange = SettingsConstants.CARDS_PER_SESSION_MIN..SettingsConstants.CARDS_PER_SESSION_MAX,
                    steps = SettingsConstants.CARDS_PER_SESSION_STEPS,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overrideCardsPerSession
                )
            }

            // Auto-show answer override
            SettingOverrideSection(
                label = "Auto-show Description",
                overridden = overrideAutoShowAnswer,
                onOverrideChange = { overrideAutoShowAnswer = it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = overrideAutoShowAnswer) { autoShowAnswer = !autoShowAnswer },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Show description immediately when viewing cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoShowAnswer,
                        onCheckedChange = { autoShowAnswer = it },
                        enabled = overrideAutoShowAnswer
                    )
                }
            }

            // Randomize due cards override
            SettingOverrideSection(
                label = "Randomize Due Cards",
                overridden = overrideRandomizeDueCards,
                onOverrideChange = { overrideRandomizeDueCards = it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = overrideRandomizeDueCards) { randomizeDueCards = !randomizeDueCards },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Shuffle cards with similar due dates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = randomizeDueCards,
                        onCheckedChange = { randomizeDueCards = it },
                        enabled = overrideRandomizeDueCards
                    )
                }
            }

            // Sampling bucket size override
            SettingOverrideSection(
                label = "Sampling Bucket Size",
                overridden = overrideRandomizeBucketHours,
                onOverrideChange = { overrideRandomizeBucketHours = it }
            ) {
                val currentBucketIndex = SettingsConstants.findPresetIndex(bucketPresets, randomizeBucketHours)
                Text(
                    bucketPresets[currentBucketIndex].second,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = currentBucketIndex.toFloat(),
                    onValueChange = { newIndex ->
                        randomizeBucketHours = bucketPresets[newIndex.roundToInt()].first
                    },
                    valueRange = 0f..(bucketPresets.size - 1).toFloat(),
                    steps = bucketPresets.size - 2,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overrideRandomizeBucketHours
                )
            }

            // Practice days override
            SettingOverrideSection(
                label = "Practice Days",
                overridden = overridePracticeDays,
                onOverrideChange = { overridePracticeDays = it }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SettingsConstants.DAY_LABELS.forEach { (day, label) ->
                        val selected = practiceDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (overridePracticeDays) {
                                    practiceDays = if (selected && practiceDays.size > 1) {
                                        practiceDays - day
                                    } else if (!selected) {
                                        practiceDays + day
                                    } else {
                                        practiceDays
                                    }
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.size(width = 42.dp, height = 36.dp),
                            enabled = overridePracticeDays,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Text(
                    if (practiceDays.size == 7) "Daily" else "${practiceDays.size} days per week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Maximum interval override
            SettingOverrideSection(
                label = "Maximum Interval",
                overridden = overrideMaximumInterval,
                onOverrideChange = { overrideMaximumInterval = it }
            ) {
                val currentPresetIndex = SettingsConstants.findPresetIndex(intervalPresets, maximumInterval)
                Text(
                    intervalPresets[currentPresetIndex].second,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = currentPresetIndex.toFloat(),
                    onValueChange = { newIndex ->
                        maximumInterval = intervalPresets[newIndex.roundToInt()].first
                    },
                    valueRange = 0f..(intervalPresets.size - 1).toFloat(),
                    steps = intervalPresets.size - 2,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overrideMaximumInterval
                )
            }

            // FSRS desired retention override
            SettingOverrideSection(
                label = "FSRS Desired Retention",
                overridden = overrideFsrsRetention,
                onOverrideChange = { overrideFsrsRetention = it }
            ) {
                RetentionSelector(
                    retentionPercent = fsrsRetention,
                    onRetentionChange = { fsrsRetention = it },
                    cardsInRotation = groupCardCount,
                    scheduleDaysPerWeek = practiceDays.size,
                    scheduleSetsPerPractice = cardsPerSession,
                    historyEstimate = practiceScheduleEstimate,
                    historyWindowDays = historyWindowDays,
                    onHistoryWindowDaysChange = onHistoryWindowDaysChange,
                    enabled = overrideFsrsRetention
                )
                Text(
                    "Target recall probability when a card comes due (FSRS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FsrsRetentionPreview(retentionPercent = fsrsRetention)
            }

            // FSRS interval fuzzing override
            SettingOverrideSection(
                label = "FSRS Interval Fuzzing",
                overridden = overrideFsrsEnableFuzzing,
                onOverrideChange = { overrideFsrsEnableFuzzing = it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = overrideFsrsEnableFuzzing) { fsrsEnableFuzzing = !fsrsEnableFuzzing },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Add \u22645\u00a0% random variance to FSRS intervals to spread reviews",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = fsrsEnableFuzzing,
                        onCheckedChange = { fsrsEnableFuzzing = it },
                        enabled = overrideFsrsEnableFuzzing
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Unsaved changes warning dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Leaving now will discard them.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

@Composable
private fun SettingOverrideSection(
    label: String,
    overridden: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOverrideChange(!overridden) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (overridden) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Checkbox(
                checked = overridden,
                onCheckedChange = { onOverrideChange(it) }
            )
        }
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}
