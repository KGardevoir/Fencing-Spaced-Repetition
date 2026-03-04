package com.fencing.spacedrepetition.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
private const val PREVIEW_W8  = 1.8722  // base for recall-stability multiplier
private const val PREVIEW_W9  = 0.1666  // stability exponent in recall formula
private const val PREVIEW_W10 = 0.796   // retrievability sensitivity in recall formula
private const val PREVIEW_W15 = 0.6014  // hard-rating penalty
private const val PREVIEW_W16 = 1.8729  // easy-rating bonus
private const val PREVIEW_W20 = 0.1542  // forgetting-curve decay (trainable in FSRS-6)

// factor derived so that R(S, S) = 90 % regardless of the user's retention setting
private val PREVIEW_FACTOR: Double = 0.9.pow(-1.0 / PREVIEW_W20) - 1 // ≈ 0.980

/** Compute the FSRS-6 next review interval (days) for a given stability and retention target. */
private fun previewInterval(stability: Double, retentionPercent: Int): Int {
    val retention = (retentionPercent / 100.0).coerceIn(0.1, 0.99)
    val decay = -PREVIEW_W20
    return (stability / PREVIEW_FACTOR * (retention.pow(1.0 / decay) - 1))
        .toInt().coerceIn(1, 3650)
}

/**
 * Compute the post-recall stability for HARD, GOOD, or EASY using FSRS-6 default weights.
 *
 * @param stability   Current card stability (days).
 * @param difficulty  Card difficulty (1–10; 5 = average).
 * @param retrievability Probability of recall at review time (e.g. 0.9 for an on-time review).
 * @param hardPenalty Use [PREVIEW_W15] for HARD, 1.0 otherwise.
 * @param easyBonus   Use [PREVIEW_W16] for EASY, 1.0 otherwise.
 */
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
 * Expandable preview that shows how different FSRS-6 retention targets and card grades
 * translate to review intervals, helping users choose a meaningful retention value.
 *
 * **Retention table** – columns are a set of key retention values (always including the
 * current selection, highlighted in primary colour); rows are three typical stability
 * milestones (Young / Mid / Mature).
 *
 * **Grade effects** – shows the next interval for HARD / GOOD / EASY on a 30-day card
 * reviewed exactly on schedule, using FSRS-6 default weights and average difficulty.
 *
 * @param retentionPercent The currently selected desired-retention value (integer %, e.g. 90).
 */
@Composable
fun FsrsRetentionPreview(retentionPercent: Int) {
    var expanded by remember { mutableStateOf(false) }

    // Always include the selected value in the comparison columns (sorted).
    val baseColumns = listOf(70, 80, 90, 95, 97)
    val columns = if (retentionPercent in baseColumns) baseColumns
                  else (baseColumns + retentionPercent).sorted()

    // Stability milestones (days) → row label.
    val stabilityRows = listOf(
        7   to "Young  (7d)",
        30  to "Mid   (30d)",
        180 to "Mature(180d)"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Toggle button ──────────────────────────────────────────────────────
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Part 1: Interval vs retention table ────────────────────────
                Text(
                    "Next interval by retention — lower % = longer gaps, fewer reviews",
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
                            columns.forEach { r ->
                                val isSelected = r == retentionPercent
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            if (isSelected) Modifier.background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.shapes.extraSmall
                                            ) else Modifier
                                        )
                                        .padding(vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$r%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))

                        // Data rows
                        stabilityRows.forEach { (stab, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.width(80.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                columns.forEach { r ->
                                    val interval = previewInterval(stab.toDouble(), r)
                                    val isSelected = r == retentionPercent
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .then(
                                                if (isSelected) Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.shapes.extraSmall
                                                ) else Modifier
                                            )
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = formatDays(interval),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Part 2: Grade effects at current retention ─────────────────
                val r30 = retentionPercent / 100.0   // retrievability for on-time review
                val stab30 = 30.0                    // representative "established" card
                val diff   = 5.0                     // average difficulty

                val hardNextInterval = previewInterval(
                    previewRecallStability(stab30, diff, r30, PREVIEW_W15, 1.0),
                    retentionPercent
                )
                val goodNextInterval = previewInterval(
                    previewRecallStability(stab30, diff, r30, 1.0, 1.0),
                    retentionPercent
                )
                val easyNextInterval = previewInterval(
                    previewRecallStability(stab30, diff, r30, 1.0, PREVIEW_W16),
                    retentionPercent
                )

                Text(
                    "Grade effects on a 30-day card reviewed on schedule at $retentionPercent% (avg. difficulty):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(
                            "Again" to "Relearn",
                            "Hard"  to formatDays(hardNextInterval),
                            "Good"  to formatDays(goodNextInterval),
                            "Easy"  to formatDays(easyNextInterval)
                        ).forEach { (grade, next) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = grade,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = next,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Text(
                    "Estimates use FSRS-6 default weights. Actual intervals vary by card difficulty and review history.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
