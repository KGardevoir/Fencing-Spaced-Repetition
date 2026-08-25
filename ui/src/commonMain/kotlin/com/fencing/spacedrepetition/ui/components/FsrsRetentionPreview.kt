// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.pow

// FSRS-6 default weights used for preview estimates
private const val PREVIEW_W0  = 0.212   // initial stability — Again
private const val PREVIEW_W1  = 1.2931  // initial stability — Hard
private const val PREVIEW_W2  = 2.3065  // initial stability — Good
private const val PREVIEW_W3  = 8.2956  // initial stability — Easy
private const val PREVIEW_W8  = 1.8722  // base for recall-stability multiplier
private const val PREVIEW_W9  = 0.1666  // stability exponent in recall formula
private const val PREVIEW_W10 = 0.796   // retrievability sensitivity in recall formula
private const val PREVIEW_W15 = 0.6014  // hard-rating penalty
private const val PREVIEW_W16 = 1.8729  // easy-rating bonus
private const val PREVIEW_W20 = 0.1542  // forgetting-curve decay (trainable in FSRS-6)

// factor derived so that R(S, S) = 90 % regardless of the user's retention setting
private val PREVIEW_FACTOR: Double = 0.9.pow(-1.0 / PREVIEW_W20) - 1 // ≈ 0.980

private fun previewInterval(stability: Double, retentionPercent: Int): Int {
    val retention = (retentionPercent / 100.0).coerceIn(0.1, 0.99)
    val decay = -PREVIEW_W20
    return (stability / PREVIEW_FACTOR * (retention.pow(1.0 / decay) - 1))
        .toInt().coerceIn(1, 3650)
}

private fun previewRecallStability(
    stability: Double,
    difficulty: Double,
    retrievability: Double,
    hardPenalty: Double = 1.0,
    easyBonus: Double = 1.0
): Double = stability * (
    1.0 + exp(PREVIEW_W8) * (11.0 - difficulty) *
    stability.pow(-PREVIEW_W9) *
    (exp((1.0 - retrievability) * PREVIEW_W10) - 1.0) *
    hardPenalty * easyBonus
)

private fun formatDays(days: Int): String = when {
    days >= 730  -> "${days / 365}yr"
    days >= 365  -> "1yr"
    days >= 60   -> "${days / 30}mo"
    else         -> "${days}d"
}

/**
 * Expandable preview showing how grading a card affects its next review interval at the
 * current retention target.
 *
 * Rows are three card-age milestones; columns are the four possible grades. All cells
 * assume the card is reviewed exactly on schedule and has average difficulty.
 *
 * @param retentionPercent The currently selected desired-retention value (integer %, e.g. 90).
 */
@Composable
fun FsrsRetentionPreview(retentionPercent: Int) {
    var expanded by remember { mutableStateOf(false) }

    val retrievability = retentionPercent / 100.0
    val diff = 5.0 // average difficulty

    // Rows grouped by section; null stability = section divider label only
    data class RowSpec(val label: String, val stability: Double?, val isSectionHeader: Boolean = false)
    val rows = listOf(
        RowSpec("1st review",    null,         isSectionHeader = true),
        RowSpec("Again (0.2d)", PREVIEW_W0),
        RowSpec("Hard  (1.3d)", PREVIEW_W1),
        RowSpec("Good  (2.3d)", PREVIEW_W2),
        RowSpec("Easy  (8.3d)", PREVIEW_W3),
        RowSpec("Later reviews", null,         isSectionHeader = true),
        RowSpec("Young  (7d)",   7.0),
        RowSpec("Mid   (30d)",  30.0),
        RowSpec("Mature(180d)", 180.0)
    )

    // Grade columns: label + recall penalty/bonus for the 2nd review
    data class GradeSpec(val label: String, val hardPenalty: Double, val easyBonus: Double)
    val grades = listOf(
        GradeSpec("Hard", PREVIEW_W15, 1.0),
        GradeSpec("Good", 1.0,         1.0),
        GradeSpec("Easy", 1.0,         PREVIEW_W16)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.Start),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Spacing preview",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Next interval after grading an on-schedule card at $retentionPercent% retention (avg. difficulty):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        // Header row
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.width(80.dp))
                            // Again column header
                            Text(
                                text = "Again",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            grades.forEach { grade ->
                                Text(
                                    text = grade.label,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))

                        rows.forEach { row ->
                            if (row.isSectionHeader) {
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 1.dp))
                                Text(
                                    text = row.label,
                                    modifier = Modifier.padding(bottom = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = row.label,
                                        modifier = Modifier.width(80.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // Again always sends the card back to relearning
                                    Text(
                                        text = "Relearn",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    grades.forEach { grade ->
                                        val nextStab = previewRecallStability(
                                            row.stability!!, diff, retrievability,
                                            grade.hardPenalty, grade.easyBonus
                                        )
                                        Text(
                                            text = formatDays(previewInterval(nextStab, retentionPercent)),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    "Estimates use FSRS-6 default weights. Actual intervals vary by card history.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
