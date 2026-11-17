// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.ui.components.ConceptSchedulingSwitch
import com.fencing.spacedrepetition.ui.components.BucketSizeSlider
import com.fencing.spacedrepetition.ui.components.CardsPerSessionSlider
import com.fencing.spacedrepetition.ui.components.RandomizeDueCardsSwitch
import com.fencing.spacedrepetition.ui.components.FsrsRetentionPreview
import com.fencing.spacedrepetition.ui.components.FuzzingSwitch
import com.fencing.spacedrepetition.ui.components.MaximumIntervalSlider
import com.fencing.spacedrepetition.ui.components.PracticeDaysPicker
import com.fencing.spacedrepetition.ui.components.RetentionSelector

/**
 * The Global learning group's settings: the app-wide defaults that Global
 * groups use and that every other learning group can override.
 *
 * Changes are written as they are made, as on the Settings screen these used
 * to live on, so there is nothing to save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalLearningSettingsScreen(
    totalCards: Int,
    cardsPerSession: Int,
    randomizeDueCards: Boolean,
    randomizeBucketHours: Int,
    practiceDays: Set<Int>,
    maximumInterval: Int,
    fsrsRetention: Int,
    fsrsEnableFuzzing: Boolean,
    conceptScheduling: Boolean,
    practiceScheduleEstimate: ScheduleEstimate?,
    historyWindowDays: Int,
    onSetCardsPerSession: (Int) -> Unit,
    onSetRandomizeDueCards: (Boolean) -> Unit,
    onSetRandomizeBucketHours: (Int) -> Unit,
    onSetPracticeDays: (Set<Int>) -> Unit,
    onSetMaximumInterval: (Int) -> Unit,
    onSetFsrsRetention: (Int) -> Unit,
    onSetFsrsEnableFuzzing: (Boolean) -> Unit,
    onSetConceptScheduling: (Boolean) -> Unit,
    onSetHistoryWindowDays: (Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global") },
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
            Text(
                "Defaults for every learning group.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            // Not a default: Global's own switch. Each learning group has one of its own.
            ConceptSchedulingSwitch(conceptScheduling, onSetConceptScheduling)

            GlobalSetting("Cards per Session", null) {
                CardsPerSessionSlider(cardsPerSession, onSetCardsPerSession)
            }
            GlobalSetting("Randomize Due Cards", null) {
                RandomizeDueCardsSwitch(randomizeDueCards, onSetRandomizeDueCards)
            }
            // Only matters while shuffling is on, as on the Settings screen it came from.
            if (randomizeDueCards) {
                GlobalSetting("Sampling Bucket Size", null) {
                    BucketSizeSlider(randomizeBucketHours, onSetRandomizeBucketHours)
                }
            }
            GlobalSetting("Practice Days", "Cards come due on these days.") {
                PracticeDaysPicker(practiceDays, onSetPracticeDays)
            }
            GlobalSetting("Maximum Interval", null) {
                MaximumIntervalSlider(maximumInterval, onSetMaximumInterval)
            }
            GlobalSetting(
                "FSRS Desired Retention",
                "Chance of remembering a card when due."
            ) {
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
                FsrsRetentionPreview(retentionPercent = fsrsRetention)
            }
            GlobalSetting("FSRS Interval Fuzzing", null) {
                FuzzingSwitch(fsrsEnableFuzzing, onSetFsrsEnableFuzzing)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GlobalSetting(label: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
