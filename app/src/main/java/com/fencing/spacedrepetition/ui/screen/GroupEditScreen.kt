package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    globalSm2IntervalModifier: Int,
    onSave: (Group) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isNewGroup = group.id == 0L

    var name by remember { mutableStateOf(group.name) }
    var description by remember { mutableStateOf(group.description) }
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

    var overrideSm2IntervalModifier by remember { mutableStateOf(group.sm2IntervalModifier != null) }
    var sm2IntervalModifier by remember { mutableIntStateOf(group.sm2IntervalModifier ?: globalSm2IntervalModifier) }

    val intervalPresets = SettingsConstants.INTERVAL_PRESETS
    val bucketPresets = SettingsConstants.BUCKET_PRESETS
    val fsrsRetentionPresets = SettingsConstants.FSRS_RETENTION_PRESETS
    val sm2ModifierPresets = SettingsConstants.SM2_MODIFIER_PRESETS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewGroup) "Add Group" else "Edit Group") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val updatedGroup = group.copy(
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
                                sm2IntervalModifier = if (overrideSm2IntervalModifier) sm2IntervalModifier else null
                            )
                            onSave(updatedGroup)
                        },
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
                val currentRetentionIndex = SettingsConstants.findPresetIndex(fsrsRetentionPresets, fsrsRetention)
                Text(
                    fsrsRetentionPresets[currentRetentionIndex].second,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = currentRetentionIndex.toFloat(),
                    onValueChange = { newIndex ->
                        fsrsRetention = fsrsRetentionPresets[newIndex.roundToInt()].first
                    },
                    valueRange = 0f..(fsrsRetentionPresets.size - 1).toFloat(),
                    steps = fsrsRetentionPresets.size - 2,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overrideFsrsRetention
                )
                Text(
                    "Target recall probability when a card comes due (FSRS). 80–92\u00a0% suits most learners.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // SM-2 interval modifier override
            SettingOverrideSection(
                label = "SM-2 Interval Modifier",
                overridden = overrideSm2IntervalModifier,
                onOverrideChange = { overrideSm2IntervalModifier = it }
            ) {
                val currentModifierIndex = SettingsConstants.findPresetIndex(sm2ModifierPresets, sm2IntervalModifier)
                Text(
                    sm2ModifierPresets[currentModifierIndex].second,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = currentModifierIndex.toFloat(),
                    onValueChange = { newIndex ->
                        sm2IntervalModifier = sm2ModifierPresets[newIndex.roundToInt()].first
                    },
                    valueRange = 0f..(sm2ModifierPresets.size - 1).toFloat(),
                    steps = sm2ModifierPresets.size - 2,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overrideSm2IntervalModifier
                )
                Text(
                    "Scales SM-2 review intervals. 100\u00a0% = default; lower = more reviews; higher = fewer reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
