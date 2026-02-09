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
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
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
    onSave: (Group) -> Unit,
    onNavigateBack: () -> Unit
) {
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

    // Preset values for maximum interval
    val intervalPresets = listOf(
        7 to "1 week", 14 to "2 weeks", 30 to "1 month", 60 to "2 months",
        90 to "3 months", 180 to "6 months", 365 to "1 year", 730 to "2 years",
        1825 to "5 years", 3650 to "10 years"
    )

    // Preset values for bucket size
    val bucketPresets = listOf(
        24 to "1 day", 72 to "3 days", 168 to "1 week", 336 to "2 weeks", 672 to "4 weeks"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Group") },
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
                                maximumInterval = if (overrideMaximumInterval) maximumInterval else null
                            )
                            onSave(updatedGroup)
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save")
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
                    valueRange = 1f..6f,
                    steps = 4,
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
                val currentBucketIndex = bucketPresets.indexOfFirst { it.first >= randomizeBucketHours }
                    .let { if (it == -1) bucketPresets.size - 1 else it }
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
                    val dayLabels = listOf(
                        1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S"
                    )
                    dayLabels.forEach { (day, label) ->
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
                val currentPresetIndex = intervalPresets.indexOfFirst { it.first >= maximumInterval }
                    .let { if (it == -1) intervalPresets.size - 1 else it }
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
