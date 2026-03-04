package com.fencing.spacedrepetition.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.ReviewLogWithCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val sessions by viewModel.completedSessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice History") },
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
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "No practice history yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Complete a practice session to see it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionHistoryCard(
                        session = session,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun SessionHistoryCard(
    session: PracticeSession,
    viewModel: HistoryViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val reviewLogs by viewModel.getReviewLogsForSession(session.id).collectAsState(initial = emptyList())

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val sessionDate = remember(session.startTime) { Date(session.startTime) }

    val gradeCounts = remember(reviewLogs) {
        reviewLogs.groupBy { it.reviewLog.grade }.mapValues { it.value.size }
    }
    val cardCount = session.cardIds.split(",").filter { it.isNotBlank() }.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Session header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateFormatter.format(sessionDate),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = timeFormatter.format(sessionDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$cardCount card${if (cardCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Grade summary chips
            if (reviewLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GradeChipIfNonZero(gradeCounts[1], "Again", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    GradeChipIfNonZero(gradeCounts[2], "Hard", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    GradeChipIfNonZero(gradeCounts[3], "Good", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    GradeChipIfNonZero(gradeCounts[4], "Easy", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Expanded detail
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    if (reviewLogs.isEmpty()) {
                        Text(
                            text = "No graded cards in this session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        reviewLogs.forEach { logWithCard ->
                            ReviewLogRow(logWithCard = logWithCard)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeChipIfNonZero(
    count: Int?,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    if (count != null && count > 0) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = containerColor
        ) {
            Text(
                text = "$count $label",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ReviewLogRow(logWithCard: ReviewLogWithCard) {
    val log = logWithCard.reviewLog
    val grade = Grade.fromValue(log.grade)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Grade indicator
        val gradeColor = when (grade) {
            Grade.AGAIN -> MaterialTheme.colorScheme.errorContainer
            Grade.HARD -> MaterialTheme.colorScheme.tertiaryContainer
            Grade.GOOD -> MaterialTheme.colorScheme.secondaryContainer
            Grade.EASY -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val gradeLabel = when (grade) {
            Grade.AGAIN -> "Again"
            Grade.HARD -> "Hard"
            Grade.GOOD -> "Good"
            Grade.EASY -> "Easy"
            Grade.SKIP -> "Skip"
            null -> "?"
        }
        val gradeContentColor = when (grade) {
            Grade.AGAIN -> MaterialTheme.colorScheme.onErrorContainer
            Grade.HARD -> MaterialTheme.colorScheme.onTertiaryContainer
            Grade.GOOD -> MaterialTheme.colorScheme.onSecondaryContainer
            Grade.EASY -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = gradeColor,
            modifier = Modifier.width(52.dp)
        ) {
            Text(
                text = gradeLabel,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = gradeContentColor
            )
        }

        Text(
            text = logWithCard.cardQuestion,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "+${log.scheduledDays}d",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
