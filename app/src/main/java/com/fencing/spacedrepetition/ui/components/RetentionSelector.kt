// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.algorithm.RetentionPlanner
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Plain-language trade-off estimates for a desired-retention value, derived from the
 * FSRS-6 forgetting curve with default weights (same model as [FsrsRetentionPreview]).
 */
object RetentionTradeOff {
    /** FSRS-6 default forgetting-curve decay (w20). */
    private const val DECAY = 0.1542

    /** Retention value the workload comparison is anchored to. */
    const val BASELINE_PERCENT = 90

    /** Relative interval length at [retentionPercent]; larger = longer gaps between reviews. */
    fun intervalFactor(retentionPercent: Int): Double =
        (retentionPercent / 100.0).coerceIn(0.5, 0.99).pow(-1.0 / DECAY) - 1.0

    /** Review-frequency multiplier vs. the [BASELINE_PERCENT] default (>1 = more reviews). */
    fun workloadMultiplier(retentionPercent: Int): Double =
        intervalFactor(BASELINE_PERCENT) / intervalFactor(retentionPercent)

    /** One-line summary of what [retentionPercent] means in practice. */
    fun summary(retentionPercent: Int): String {
        val forgotten = 100 - retentionPercent
        val workload = workloadMultiplier(retentionPercent)
        val workloadText = when {
            workload >= 1.05 ->
                "about ${(workload * 10).roundToInt() / 10.0}× the reviews of the $BASELINE_PERCENT% default"
            workload <= 0.95 ->
                "about ${((1 - workload) * 100).roundToInt()}% fewer reviews than the $BASELINE_PERCENT% default"
            else -> "the recommended balance of effort and recall"
        }
        return "≈$forgotten in 100 due cards forgotten — $workloadText."
    }
}

/**
 * Picker for the FSRS desired-retention setting. A schedule planner suggests a value that
 * fits the user's practice cadence (days per week × sets per practice); the "From history"
 * button fills those inputs from recent review logs, and a fine-grained slider with an
 * adjacent value readout allows manual control.
 *
 * @param retentionPercent Currently selected desired retention (integer %, e.g. 90).
 * @param onRetentionChange Called with the new integer percent when the selection changes.
 * @param cardsInRotation Number of cards the schedule has to sustain (0 hides the suggestion).
 * @param scheduleDaysPerWeek Initial days/week for the planner (from settings).
 * @param scheduleSetsPerPractice Initial sets/practice for the planner (from settings).
 * @param historyEstimate Cadence sampled from review history, applied via the "From history"
 *   button; null (too little history) disables the button.
 * @param historyWindowDays How many days of review history the estimate is fitted over.
 * @param onHistoryWindowDaysChange Called with a new valid fit window entered by the user.
 * @param enabled Whether the picker accepts input (used by group override sections).
 */
@Composable
fun RetentionSelector(
    retentionPercent: Int,
    onRetentionChange: (Int) -> Unit,
    cardsInRotation: Int,
    scheduleDaysPerWeek: Int,
    scheduleSetsPerPractice: Int,
    historyEstimate: ScheduleEstimate?,
    historyWindowDays: Int,
    onHistoryWindowDaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var daysPerWeek by remember { mutableIntStateOf(scheduleDaysPerWeek.coerceIn(1, 7)) }
    var setsPerPractice by remember { mutableIntStateOf(scheduleSetsPerPractice.coerceAtLeast(1)) }
    var windowText by remember { mutableStateOf(historyWindowDays.toString()) }
    val windowValid = windowText.toIntOrNull()
        ?.let { it in RetentionPlanner.MIN_HISTORY_WINDOW_DAYS..RetentionPlanner.MAX_HISTORY_WINDOW_DAYS }
        ?: false

    val suggestion = if (cardsInRotation > 0) {
        RetentionPlanner.suggestedRetention(
            daysPerWeek.toDouble(), setsPerPractice.toDouble(), cardsInRotation
        )
    } else null

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = MaterialTheme.shapes.small,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plan from your practice schedule",
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(
                        onClick = {
                            historyEstimate?.let {
                                daysPerWeek = it.daysPerWeek.roundToInt().coerceIn(1, 7)
                                setsPerPractice = it.setsPerPractice.roundToInt().coerceAtLeast(1)
                            }
                        },
                        enabled = enabled && historyEstimate != null
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("From history")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fit window",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = windowText,
                        onValueChange = { text ->
                            windowText = text.filter { it.isDigit() }.take(3)
                            windowText.toIntOrNull()
                                ?.takeIf {
                                    it in RetentionPlanner.MIN_HISTORY_WINDOW_DAYS..
                                        RetentionPlanner.MAX_HISTORY_WINDOW_DAYS
                                }
                                ?.let(onHistoryWindowDaysChange)
                        },
                        label = { Text("days") },
                        isError = !windowValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        enabled = enabled,
                        modifier = Modifier.width(96.dp)
                    )
                }
                if (!windowValid) {
                    Text(
                        text = "Enter ${RetentionPlanner.MIN_HISTORY_WINDOW_DAYS}–" +
                            "${RetentionPlanner.MAX_HISTORY_WINDOW_DAYS} days.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (historyEstimate == null) {
                    Text(
                        text = "Not enough practice in the last $historyWindowDays days " +
                            "to sample your history yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StepperRow(
                    label = "Practice days per week",
                    value = daysPerWeek,
                    range = 1..7,
                    enabled = enabled,
                    onValueChange = { daysPerWeek = it }
                )
                StepperRow(
                    label = "Sets per practice",
                    value = setsPerPractice,
                    range = 1..99,
                    enabled = enabled,
                    onValueChange = { setsPerPractice = it }
                )

                if (suggestion != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Suggested: $suggestion%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { onRetentionChange(suggestion) },
                            enabled = enabled && suggestion != retentionPercent
                        ) {
                            Text(if (suggestion == retentionPercent) "Applied" else "Apply")
                        }
                    }
                    Text(
                        text = "Fits $cardsInRotation cards into about " +
                            "${daysPerWeek * setsPerPractice} sets per week.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Add cards to get a suggestion for your schedule.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = retentionPercent.toFloat(),
                onValueChange = { onRetentionChange(it.roundToInt()) },
                valueRange = ThemePreferences.MIN_FSRS_RETENTION.toFloat()..
                    ThemePreferences.MAX_FSRS_RETENTION.toFloat(),
                steps = ThemePreferences.MAX_FSRS_RETENTION - ThemePreferences.MIN_FSRS_RETENTION - 1,
                modifier = Modifier.weight(1f),
                enabled = enabled
            )
            Text(
                text = "$retentionPercent%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .width(48.dp)
                    .padding(start = 8.dp)
            )
        }

        Text(
            text = RetentionTradeOff.summary(retentionPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange(value - 1) },
                enabled = enabled && value > range.first
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp)
            )
            IconButton(
                onClick = { onValueChange(value + 1) },
                enabled = enabled && value < range.last
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}
