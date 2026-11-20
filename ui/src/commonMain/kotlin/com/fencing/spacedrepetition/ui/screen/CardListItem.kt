// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.KeptListPosition
import com.fencing.spacedrepetition.ui.components.ScrollToTopOnSortChange
import com.fencing.spacedrepetition.ui.components.SelectionAction
import com.fencing.spacedrepetition.ui.components.SelectionTopBar
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.viewmodel.CardSortOption
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState
import com.fencing.spacedrepetition.ui.viewmodel.SortDirection
import com.fencing.spacedrepetition.ui.components.CsvGroupSelectionDialog
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.formatDate
import com.fencing.spacedrepetition.util.toOneDecimal
import com.fencing.spacedrepetition.util.Time

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CardListItem(
    card: Card,
    groups: List<Group>,
    /** The card's concepts, each with how much of it the card contains. */
    concepts: List<Pair<com.fencing.spacedrepetition.data.model.Concept, Double>> = emptyList(),
    learningStates: List<CardLearningState> = emptyList(),
    learningGroups: List<LearningGroup> = emptyList(),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sortOption: CardSortOption = CardSortOption.DUE_DATE,
    onEdit: () -> Unit,
    onToggleSelection: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else {
                        expanded = !expanded
                    }
                },
                onLongClick = onLongPress
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                card.isDisabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection checkbox
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.question,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!expanded) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // Due date
                        val dueDateText: String
                        val dueDateColor: androidx.compose.ui.graphics.Color
                        if (card.nextReview == 0L) {
                            dueDateText = "New card"
                            dueDateColor = MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            dueDateText = formatDate(card.nextReview)
                            val now = Time.now()
                            val diff = card.nextReview - now
                            val daysDiff = (diff / (1000 * 60 * 60 * 24)).toInt()
                            dueDateColor = when {
                                diff <= 0 -> MaterialTheme.colorScheme.error
                                daysDiff == 0 -> MaterialTheme.colorScheme.tertiary
                                daysDiff <= 3 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dueDateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = dueDateColor
                            )

                            // Display additional sort field info
                            Text(
                                text = "• ${card.fsrsReps} reviews",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Difficulty: ${card.fsrsDifficulty.toOneDecimal()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (groups.isNotEmpty()) {
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            groups.firstOrNull()?.name ?: "",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                                if (groups.size > 1) {
                                    Text(
                                        "+${groups.size - 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Concepts, the same way: the first, and how many more.
                            if (concepts.isNotEmpty()) {
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            conceptChipLabel(concepts.first().first.name, concepts.first().second),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = "Concept",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                                if (concepts.size > 1) {
                                    Text(
                                        "+${concepts.size - 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (card.isDisabled) {
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Text(
                                            "Disabled",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Block,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }

                if (!isSelectionMode) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }
            }

            if (expanded && !isSelectionMode) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Description:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                MarkdownText(
                    text = card.answer
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Groups display
                if (groups.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groups.forEach { group ->
                            AssistChip(
                                onClick = { },
                                label = { Text(group.name, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        AppIcons.CardGroups,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Concepts display
                if (concepts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        concepts.forEach { (concept, weight) ->
                            AssistChip(
                                onClick = { },
                                label = { Text(conceptChipLabel(concept.name, weight), style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Learning group states display
                val cardLearningGroups = learningGroups.filter { learningGroup ->
                    groups.any { it.learningGroupId == learningGroup.id }
                }
                if (cardLearningGroups.isNotEmpty()) {
                    Text(
                        text = "Learning Groups:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    cardLearningGroups.forEach { group ->
                        val state = learningStates.find { it.learningGroupId == group.id }
                        val stateText = if (state == null) {
                            "${group.name}: No state"
                        } else {
                            val nextReview = if (state.nextReview == 0L) {
                                "New"
                            } else {
                                formatDate(state.nextReview)
                            }
                            "${group.name}: Next ${nextReview} (${state.fsrsState})"
                        }
                        Text(
                            text = stateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Next review info
                val nextReviewText = if (card.nextReview == 0L) {
                    "New card"
                } else {
                    "Next: ${formatDate(card.nextReview)}"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nextReviewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
