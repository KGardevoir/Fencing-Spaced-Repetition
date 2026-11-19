// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Grade

/** A grade as a small coloured label -- "Again", "Hard", "Good", "Easy" or "Skip" -- as history lists it. */
@Composable
fun GradeBadge(grade: Grade?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (grade) {
        Grade.AGAIN -> scheme.errorContainer to scheme.onErrorContainer
        Grade.HARD -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Grade.GOOD -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Grade.EASY -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, modifier = modifier) {
        Text(
            text = grade?.label ?: "?",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}
