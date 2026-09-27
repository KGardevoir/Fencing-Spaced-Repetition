// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.FightDifficulty
import com.fencing.spacedrepetition.util.toTwoDecimals

/**
 * Rating of how hard the fight itself was, 1-5.
 *
 * Scales the FSRS stability gain exactly as the opponent's skill multiplier
 * does, and the two multiply. Tapping the selected rating again clears it;
 * an unrated fight is neutral, the same as a 3.
 */
@Composable
fun FightDifficultyPicker(
    selected: Int?,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Fight difficulty"
) {
    val chosen = FightDifficulty.fromRating(selected)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Sports,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = chosen?.let { "${it.label} · ×${it.multiplier.toTwoDecimals()}" } ?: "Unrated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FightDifficulty.entries.forEach { difficulty ->
                val isSelected = difficulty.rating == selected
                FilterChip(
                    selected = isSelected,
                    // Tapping the chosen rating clears it, so a fight can be
                    // put back to unrated without a separate "None" chip.
                    onClick = { onSelected(if (isSelected) null else difficulty.rating) },
                    label = {
                        Text(
                            text = difficulty.rating.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
