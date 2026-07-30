package com.fencing.spacedrepetition.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
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
 * Picker for the FSRS desired-retention setting: named presets as chips, a fine-grained
 * slider behind "Custom", and a live summary of the workload/recall trade-off.
 *
 * @param retentionPercent Currently selected desired retention (integer %, e.g. 90).
 * @param onRetentionChange Called with the new integer percent when the selection changes.
 * @param enabled Whether the picker accepts input (used by group override sections).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetentionSelector(
    retentionPercent: Int,
    onRetentionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val presets = SettingsConstants.FSRS_RETENTION_NAMED_PRESETS
    val matchesPreset = presets.any { it.first == retentionPercent }
    var customSelected by remember { mutableStateOf(!matchesPreset) }
    val showSlider = customSelected || !matchesPreset

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (percent, label) ->
                FilterChip(
                    selected = !showSlider && retentionPercent == percent,
                    onClick = {
                        customSelected = false
                        onRetentionChange(percent)
                    },
                    label = { Text("$label $percent%") },
                    enabled = enabled
                )
            }
            FilterChip(
                selected = showSlider,
                onClick = { customSelected = true },
                label = { Text("Custom") },
                enabled = enabled
            )
        }

        AnimatedVisibility(visible = showSlider) {
            Slider(
                value = retentionPercent.toFloat(),
                onValueChange = { onRetentionChange(it.roundToInt()) },
                valueRange = ThemePreferences.MIN_FSRS_RETENTION.toFloat()..
                    ThemePreferences.MAX_FSRS_RETENTION.toFloat(),
                steps = ThemePreferences.MAX_FSRS_RETENTION - ThemePreferences.MIN_FSRS_RETENTION - 1,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
        }

        Text(
            text = RetentionTradeOff.summary(retentionPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
