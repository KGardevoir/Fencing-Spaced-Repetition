// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.FsrsRetentionPreview
import com.fencing.spacedrepetition.ui.components.RetentionSelector
import kotlin.math.roundToInt

/**
 * A learning group: its name and description, which card groups are in it,
 * and the settings that schedule the learning progress they share.
 *
 * The scheduling settings are here rather than on each card group because
 * every group in a learning group grades against one state per card: two
 * groups asking for different retention on the same state would schedule it
 * whichever way the last grade happened to come from.
 *
 * @param countCards how many different cards a set of card groups holds,
 *   for the retention planner's workload estimate.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun LearningGroupEditScreen(
    learningGroup: LearningGroup,
    learningGroups: List<LearningGroup>,
    groups: List<Group>,
    globalCardsPerSession: Int,
    globalPracticeDays: Set<Int>,
    globalMaximumInterval: Int,
    globalFsrsRetention: Int,
    globalFsrsEnableFuzzing: Boolean,
    practiceScheduleEstimate: ScheduleEstimate?,
    historyWindowDays: Int,
    onHistoryWindowDaysChange: (Int) -> Unit,
    countCards: suspend (Set<Long>) -> Int,
    onSave: (LearningGroup, Set<Long>) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isNew = learningGroup.id == 0L
    val initialMemberIds = remember(learningGroup.id) {
        if (isNew) emptySet()
        else groups.filter { it.learningGroupId == learningGroup.id }.map { it.id }.toSet()
    }

    var name by rememberSaveable { mutableStateOf(learningGroup.name) }
    var description by rememberSaveable { mutableStateOf(learningGroup.description) }
    var memberIds by remember { mutableStateOf(initialMemberIds) }

    var overridePracticeDays by remember { mutableStateOf(learningGroup.practiceDays != null) }
    var practiceDays by remember { mutableStateOf(learningGroup.parsePracticeDays() ?: globalPracticeDays) }

    var overrideMaximumInterval by remember { mutableStateOf(learningGroup.maximumInterval != null) }
    var maximumInterval by remember { mutableIntStateOf(learningGroup.maximumInterval ?: globalMaximumInterval) }

    var overrideFsrsRetention by remember { mutableStateOf(learningGroup.fsrsRetention != null) }
    var fsrsRetention by remember { mutableIntStateOf(learningGroup.fsrsRetention ?: globalFsrsRetention) }

    var overrideFsrsEnableFuzzing by remember { mutableStateOf(learningGroup.fsrsEnableFuzzing != null) }
    var fsrsEnableFuzzing by remember { mutableStateOf(learningGroup.fsrsEnableFuzzing ?: globalFsrsEnableFuzzing) }

    var cardCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(memberIds) { cardCount = countCards(memberIds) }

    val intervalPresets = SettingsConstants.INTERVAL_PRESETS

    val nameTaken = learningGroups.any {
        it.id != learningGroup.id && it.name.equals(name.trim(), ignoreCase = true)
    } || name.trim().equals("Global", ignoreCase = true)

    fun buildUpdated() = learningGroup.copy(
        name = name.trim(),
        description = description.trim(),
        practiceDays = if (overridePracticeDays) practiceDays.sorted().joinToString(",") else null,
        maximumInterval = if (overrideMaximumInterval) maximumInterval else null,
        fsrsRetention = if (overrideFsrsRetention) fsrsRetention else null,
        fsrsEnableFuzzing = if (overrideFsrsEnableFuzzing) fsrsEnableFuzzing else null
    )

    val isDirty = buildUpdated() != learningGroup || memberIds != initialMemberIds
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

    // The same back handling as the group editor; see the note there.
    BackHandler(enabled = isDirty) {
        showUnsavedChangesDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add Learning Group" else "Edit Learning Group") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) showUnsavedChangesDialog = true else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(buildUpdated(), memberIds) },
                        enabled = name.isNotBlank() && !nameTaken
                    ) {
                        Text(if (isNew) "Add" else "Save")
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
            Text(
                "Learning Group Info",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text("That name is already in use") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.CardGroups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Card Groups",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "The groups in here share one set of learning progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (groups.isEmpty()) {
                Text(
                    "No card groups yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            groups.forEach { group ->
                val checked = group.id in memberIds
                val elsewhere = learningGroups
                    .find { it.id == group.learningGroupId && it.id != learningGroup.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            memberIds = if (checked) memberIds - group.id else memberIds + group.id
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            memberIds = if (it) memberIds + group.id else memberIds - group.id
                        }
                    )
                    Column {
                        Text(group.name, style = MaterialTheme.typography.bodyLarge)
                        if (elsewhere != null) {
                            Text(
                                if (checked) "Moves here from ${elsewhere.name}" else "In ${elsewhere.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Learning Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "How this learning group's progress is scheduled. Disabled overrides use the global default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

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

            SettingOverrideSection(
                label = "FSRS Desired Retention",
                overridden = overrideFsrsRetention,
                onOverrideChange = { overrideFsrsRetention = it }
            ) {
                RetentionSelector(
                    retentionPercent = fsrsRetention,
                    onRetentionChange = { fsrsRetention = it },
                    cardsInRotation = cardCount,
                    scheduleDaysPerWeek = practiceDays.size,
                    scheduleSetsPerPractice = globalCardsPerSession,
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
                        "Add ≤5 % random variance to FSRS intervals to spread reviews",
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
