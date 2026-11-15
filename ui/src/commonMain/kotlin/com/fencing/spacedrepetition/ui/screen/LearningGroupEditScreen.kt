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
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.BucketSizeSlider
import com.fencing.spacedrepetition.ui.components.CardsPerSessionSlider
import com.fencing.spacedrepetition.ui.components.RandomizeDueCardsSwitch
import com.fencing.spacedrepetition.ui.components.FuzzingSwitch
import com.fencing.spacedrepetition.ui.components.MaximumIntervalSlider
import com.fencing.spacedrepetition.ui.components.PracticeDaysPicker
import com.fencing.spacedrepetition.ui.components.FsrsRetentionPreview
import com.fencing.spacedrepetition.ui.components.RetentionSelector

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
    globalRandomizeDueCards: Boolean,
    globalRandomizeBucketHours: Int,
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

    var overrideCardsPerSession by remember { mutableStateOf(learningGroup.cardsPerSession != null) }
    var cardsPerSession by remember { mutableIntStateOf(learningGroup.cardsPerSession ?: globalCardsPerSession) }

    var overrideRandomizeDueCards by remember { mutableStateOf(learningGroup.randomizeDueCards != null) }
    var randomizeDueCards by remember { mutableStateOf(learningGroup.randomizeDueCards ?: globalRandomizeDueCards) }

    var overrideRandomizeBucketHours by remember { mutableStateOf(learningGroup.randomizeBucketHours != null) }
    var randomizeBucketHours by remember {
        mutableIntStateOf(learningGroup.randomizeBucketHours ?: globalRandomizeBucketHours)
    }

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

    val nameTaken = learningGroups.any {
        it.id != learningGroup.id && it.name.equals(name.trim(), ignoreCase = true)
    } || name.trim().equals("Global", ignoreCase = true)

    fun buildUpdated() = learningGroup.copy(
        name = name.trim(),
        description = description.trim(),
        cardsPerSession = if (overrideCardsPerSession) cardsPerSession else null,
        randomizeDueCards = if (overrideRandomizeDueCards) randomizeDueCards else null,
        randomizeBucketHours = if (overrideRandomizeBucketHours) randomizeBucketHours else null,
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
                "How much this learning group practises and how its progress is scheduled. " +
                    "Disabled overrides use the Global settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            SettingOverrideSection(
                label = "Cards per Session",
                overridden = overrideCardsPerSession,
                onOverrideChange = { overrideCardsPerSession = it }
            ) {
                CardsPerSessionSlider(cardsPerSession, { cardsPerSession = it }, enabled = overrideCardsPerSession)
            }

            SettingOverrideSection(
                label = "Randomize Due Cards",
                overridden = overrideRandomizeDueCards,
                onOverrideChange = { overrideRandomizeDueCards = it }
            ) {
                RandomizeDueCardsSwitch(randomizeDueCards, { randomizeDueCards = it }, enabled = overrideRandomizeDueCards)
            }

            SettingOverrideSection(
                label = "Sampling Bucket Size",
                overridden = overrideRandomizeBucketHours,
                onOverrideChange = { overrideRandomizeBucketHours = it }
            ) {
                BucketSizeSlider(randomizeBucketHours, { randomizeBucketHours = it }, enabled = overrideRandomizeBucketHours)
            }

            SettingOverrideSection(
                label = "Practice Days",
                overridden = overridePracticeDays,
                onOverrideChange = { overridePracticeDays = it }
            ) {
                PracticeDaysPicker(practiceDays, { practiceDays = it }, enabled = overridePracticeDays)
            }

            SettingOverrideSection(
                label = "Maximum Interval",
                overridden = overrideMaximumInterval,
                onOverrideChange = { overrideMaximumInterval = it }
            ) {
                MaximumIntervalSlider(maximumInterval, { maximumInterval = it }, enabled = overrideMaximumInterval)
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

            SettingOverrideSection(
                label = "FSRS Interval Fuzzing",
                overridden = overrideFsrsEnableFuzzing,
                onOverrideChange = { overrideFsrsEnableFuzzing = it }
            ) {
                FuzzingSwitch(fsrsEnableFuzzing, { fsrsEnableFuzzing = it }, enabled = overrideFsrsEnableFuzzing)
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
