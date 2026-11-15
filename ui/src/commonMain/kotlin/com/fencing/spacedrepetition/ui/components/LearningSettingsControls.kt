// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import kotlin.math.roundToInt

// The controls for a learning group's settings, drawn the same way for Global
// (which writes the app-wide preferences) and for a learning group of its own
// (which overrides them). Only the value is drawn here; the label, and the
// override tick where there is one, belong to the screen.

@Composable
fun CardsPerSessionSlider(value: Int, onValueChange: (Int) -> Unit, enabled: Boolean = true) {
    Text("$value", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = SettingsConstants.CARDS_PER_SESSION_MIN..SettingsConstants.CARDS_PER_SESSION_MAX,
        steps = SettingsConstants.CARDS_PER_SESSION_STEPS,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    )
}

/** Day chips; the last selected day cannot be turned off. */
@Composable
fun PracticeDaysPicker(days: Set<Int>, onDaysChange: (Set<Int>) -> Unit, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SettingsConstants.DAY_LABELS.forEach { (day, label) ->
            val selected = day in days
            FilterChip(
                selected = selected,
                onClick = {
                    when {
                        !selected -> onDaysChange(days + day)
                        days.size > 1 -> onDaysChange(days - day)
                    }
                },
                label = { Text(label) },
                modifier = Modifier.size(width = 42.dp, height = 36.dp),
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
    Text(
        if (days.size == 7) "Daily" else "${days.size} days per week",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun MaximumIntervalSlider(value: Int, onValueChange: (Int) -> Unit, enabled: Boolean = true) {
    val presets = SettingsConstants.INTERVAL_PRESETS
    val index = SettingsConstants.findPresetIndex(presets, value)
    Text(presets[index].second, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Slider(
        value = index.toFloat(),
        onValueChange = { onValueChange(presets[it.roundToInt()].first) },
        valueRange = 0f..(presets.size - 1).toFloat(),
        steps = presets.size - 2,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    )
}

@Composable
fun FuzzingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Add ≤5 % random variance to FSRS intervals to spread reviews",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun RandomizeDueCardsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Shuffle cards with similar due dates for variety",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun BucketSizeSlider(value: Int, onValueChange: (Int) -> Unit, enabled: Boolean = true) {
    val presets = SettingsConstants.BUCKET_PRESETS
    val index = SettingsConstants.findPresetIndex(presets, value)
    Text(presets[index].second, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Slider(
        value = index.toFloat(),
        onValueChange = { onValueChange(presets[it.roundToInt()].first) },
        valueRange = 0f..(presets.size - 1).toFloat(),
        steps = presets.size - 2,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    )
    Text(
        "Cards due within the same bucket are shuffled randomly. Smaller buckets preserve due-date ordering more strictly.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
