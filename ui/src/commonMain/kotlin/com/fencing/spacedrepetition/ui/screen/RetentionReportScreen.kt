// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.algorithm.CardRetention
import com.fencing.spacedrepetition.algorithm.LearningContextReport
import com.fencing.spacedrepetition.algorithm.RetentionPeriod
import com.fencing.spacedrepetition.algorithm.RetentionPoint
import com.fencing.spacedrepetition.algorithm.RetentionReport
import com.fencing.spacedrepetition.algorithm.RetentionSummary
import com.fencing.spacedrepetition.util.formatDate
import com.fencing.spacedrepetition.util.formatDateWithoutYear
import com.fencing.spacedrepetition.util.toOneDecimal
import kotlin.math.roundToInt

private enum class ReportTab(val label: String) {
    OVERVIEW("Overview"),
    GROUPS("Groups")
}

/**
 * How retention is progressing: the whole collection and each learning
 * context on the overview, and any one card group or learning group --
 * chosen from a dropdown -- with its trend and every card in it.
 *
 * Every summary shows two numbers that are easy to confuse, so both are
 * labelled plainly: "Predicted now" is FSRS's estimate of what you would
 * remember if tested today; "Recall" is what actually happened when you were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetentionReportScreen(
    report: RetentionReport?,
    period: RetentionPeriod,
    onPeriodChange: (RetentionPeriod) -> Unit,
    onNavigateBack: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(ReportTab.OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Retention") },
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
        ) {
            TabRow(selectedTabIndex = tab.ordinal) {
                ReportTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) }
                    )
                }
            }

            if (report == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            when (tab) {
                ReportTab.OVERVIEW -> OverviewTab(report, period, onPeriodChange)
                ReportTab.GROUPS -> GroupsTab(report, period, onPeriodChange)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Overview: the whole collection, then each learning context
// ---------------------------------------------------------------------------

@Composable
private fun OverviewTab(
    report: RetentionReport,
    period: RetentionPeriod,
    onPeriodChange: (RetentionPeriod) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PeriodSelector(period, onPeriodChange) }
        item {
            SummaryCard(
                title = "All learning",
                subtitle = "Every card in every learning context",
                summary = report.overall,
                period = period,
                emphasised = true
            )
        }
        if (report.cardGroups.isNotEmpty()) {
            item { SectionHeader("Card groups") }
            items(report.cardGroups, key = { "group-${it.groupId}" }) { group ->
                SummaryCard(
                    title = group.name,
                    subtitle = "Learns in ${group.learningContextName}",
                    summary = group.summary,
                    period = period
                )
            }
        }
        item { SectionHeader("Learning groups") }
        items(report.learningContexts, key = { "ctx-${it.learningGroupId}" }) { ctx ->
            SummaryCard(
                title = ctx.name,
                subtitle = contextSubtitle(ctx),
                summary = ctx.summary,
                period = period
            )
        }
    }
}

private fun contextSubtitle(ctx: LearningContextReport): String = when {
    ctx.learningGroupId == null && ctx.cardGroupNames.isEmpty() -> "Every card's own progress"
    ctx.learningGroupId == null -> "Every card's own progress · " + ctx.cardGroupNames.joinToString()
    ctx.cardGroupNames.isEmpty() -> "No card groups learn here"
    else -> ctx.cardGroupNames.joinToString()
}

// ---------------------------------------------------------------------------
// Groups: one card group or learning group at a time, and its cards
// ---------------------------------------------------------------------------

/** What the Groups tab is showing: a card group, or a learning context (null id is Global). */
private sealed interface GroupSelection {
    data class CardGroup(val groupId: Long) : GroupSelection
    data class LearningContext(val learningGroupId: Long?) : GroupSelection
}

private fun GroupSelection.key(): String = when (this) {
    is GroupSelection.CardGroup -> "g:$groupId"
    is GroupSelection.LearningContext -> "l:${learningGroupId ?: ""}"
}

private fun selectionFromKey(key: String?): GroupSelection? = when {
    key == null -> null
    key.startsWith("g:") -> key.removePrefix("g:").toLongOrNull()?.let { GroupSelection.CardGroup(it) }
    key.startsWith("l:") -> GroupSelection.LearningContext(key.removePrefix("l:").toLongOrNull())
    else -> null
}

private class SelectedGroup(
    val selection: GroupSelection,
    val title: String,
    val subtitle: String,
    val summary: RetentionSummary,
    val cards: List<CardRetention>
)

private fun resolve(report: RetentionReport, selection: GroupSelection?): SelectedGroup? {
    when (selection) {
        is GroupSelection.CardGroup -> report.cardGroups.firstOrNull { it.groupId == selection.groupId }
            ?.let { return SelectedGroup(selection, it.name, "Card group · learns in ${it.learningContextName}", it.summary, it.cards) }
        is GroupSelection.LearningContext -> report.learningContexts.firstOrNull { it.learningGroupId == selection.learningGroupId }
            ?.let { return SelectedGroup(selection, it.name, "Learning group · " + contextSubtitle(it), it.summary, it.cards) }
        null -> {}
    }
    // Nothing chosen yet, or the chosen group was deleted: the first card group, else Global.
    report.cardGroups.firstOrNull()?.let { return resolve(report, GroupSelection.CardGroup(it.groupId)) }
    return report.learningContexts.firstOrNull()?.let { resolve(report, GroupSelection.LearningContext(it.learningGroupId)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsTab(
    report: RetentionReport,
    period: RetentionPeriod,
    onPeriodChange: (RetentionPeriod) -> Unit
) {
    // Saved as a string key so the choice survives rotation and the report recomputing.
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedCard by remember { mutableStateOf<Long?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val selected = resolve(report, selectionFromKey(selectedKey)) ?: return
    val shown = selected.cards.filter { query.isBlank() || it.question.contains(query, ignoreCase = true) }

    fun choose(selection: GroupSelection) {
        selectedKey = selection.key()
        expandedCard = null
        menuOpen = false
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ExposedDropdownMenuBox(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = !menuOpen }
            ) {
                OutlinedTextField(
                    value = selected.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Group") },
                    supportingText = { Text(if (selected.selection is GroupSelection.CardGroup) "Card group" else "Learning group") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    if (report.cardGroups.isNotEmpty()) {
                        MenuHeader("Card groups")
                        report.cardGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = { choose(GroupSelection.CardGroup(group.groupId)) },
                                trailingIcon = { MenuPercent(group.summary.predictedRetentionNow) }
                            )
                        }
                        HorizontalDivider()
                    }
                    MenuHeader("Learning groups")
                    report.learningContexts.forEach { ctx ->
                        DropdownMenuItem(
                            text = { Text(ctx.name) },
                            onClick = { choose(GroupSelection.LearningContext(ctx.learningGroupId)) },
                            trailingIcon = { MenuPercent(ctx.summary.predictedRetentionNow) }
                        )
                    }
                }
            }
        }
        item { PeriodSelector(period, onPeriodChange) }
        item {
            SummaryCard(
                title = selected.title,
                subtitle = selected.subtitle,
                summary = selected.summary,
                period = period
            )
        }
        item {
            Text(
                "Cards",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search cards") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
        }
        item {
            Text(
                "${selected.summary.learnedCount} of ${selected.summary.cardCount} cards learned · weakest first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (shown.isEmpty()) {
            item { EmptyNote(if (selected.cards.isEmpty()) "No cards in ${selected.title}." else "No matching cards.") }
        }
        items(shown, key = { "card-${selected.selection.key()}-${it.cardId}" }) { card ->
            CardRow(
                card = card,
                expanded = expandedCard == card.cardId,
                onClick = { expandedCard = if (expandedCard == card.cardId) null else card.cardId }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun MenuPercent(value: Double?) {
    Text(
        value?.let(::percent) ?: "–",
        style = MaterialTheme.typography.labelMedium,
        color = retentionColor(value)
    )
}

@Composable
private fun CardRow(card: CardRetention, expanded: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.question,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    card.retrievabilityNow?.let(::percent) ?: "New",
                    style = MaterialTheme.typography.titleMedium,
                    color = retentionColor(card.retrievabilityNow)
                )
            }
            RetentionBar(card.retrievabilityNow)
            Text(
                buildList {
                    add(stateLabel(card.state))
                    if (card.isLearned) add("stability ${days(card.stabilityDays)}")
                    if (card.reviews > 0) add("recall ${card.recalled}/${card.reviews}")
                    if (card.lapses > 0) add("${card.lapses} lapse${if (card.lapses == 1) "" else "s"}")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                if (card.isLearned) {
                    DetailLine("Last review", formatDate(card.lastReview))
                    if (card.nextReview > 0) DetailLine("Next review", formatDate(card.nextReview))
                    DetailLine("Difficulty", card.difficulty.toOneDecimal() + " / 10")
                    DetailLine("Reviews", card.reps.toString())
                }
                if (card.history.isEmpty()) {
                    Text(
                        "No graded reviews here yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Review history", style = MaterialTheme.typography.labelLarge)
                    CardHistoryChart(card)
                    GradeLegend()
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun PeriodSelector(period: RetentionPeriod, onPeriodChange: (RetentionPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Trend by", style = MaterialTheme.typography.bodyMedium)
        FilterChip(
            selected = period == RetentionPeriod.WEEK,
            onClick = { onPeriodChange(RetentionPeriod.WEEK) },
            label = { Text("Week") }
        )
        FilterChip(
            selected = period == RetentionPeriod.MONTH,
            onClick = { onPeriodChange(RetentionPeriod.MONTH) },
            label = { Text("Month") }
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    subtitle: String,
    summary: RetentionSummary,
    period: RetentionPeriod,
    emphasised: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (emphasised) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Stat("Predicted now", summary.predictedRetentionNow?.let(::percent) ?: "–", Modifier.weight(1f))
                Stat("Recall, 30 days", summary.recentRecall?.let(::percent) ?: "–", Modifier.weight(1f))
                Stat("Recall, all time", summary.measuredRecall?.let(::percent) ?: "–", Modifier.weight(1f))
            }
            Text(
                buildList {
                    add("${summary.learnedCount}/${summary.cardCount} learned")
                    if (summary.dueCount > 0) add("${summary.dueCount} due")
                    summary.averageStabilityDays?.let { add("avg stability ${days(it)}") }
                    add("${summary.reviews} review${if (summary.reviews == 1) "" else "s"}")
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (summary.trend.isNotEmpty()) {
                TrendChart(summary.trend, period)
                TrendLegend()
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RetentionBar(value: Double?) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = retentionColor(value)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track)
    ) {
        if (value != null) {
            Box(
                Modifier
                    .fillMaxWidth(value.toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(fill)
            )
        }
    }
}

/**
 * Measured recall per period as a solid line with a dot per period, FSRS's
 * prediction for the same reviews as a dashed line. The y axis starts at
 * 50 % (or lower if the data does) because retention rarely goes below it
 * and a 0-100 axis flattens every change worth seeing.
 */
@Composable
private fun TrendChart(points: List<RetentionPoint>, period: RetentionPeriod) {
    val measuredColor = MaterialTheme.colorScheme.primary
    val predictedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val lowest = points.minOf { minOf(it.recall, it.predicted ?: 1.0) }
    val yMin = if (lowest >= 0.5) 0.5 else (lowest * 10).toInt() / 10.0
    val yRange = 1.0 - yMin

    Column {
        Row {
            Column(
                Modifier.height(120.dp).padding(end = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("100%", style = labelStyle, color = labelColor)
                Text(percent(yMin), style = labelStyle, color = labelColor)
            }
            Canvas(Modifier.weight(1f).height(120.dp)) {
                val w = size.width
                val h = size.height
                fun x(i: Int) = if (points.size == 1) w / 2 else w * i / (points.size - 1)
                fun y(v: Double) = (h * (1 - (v - yMin) / yRange)).toFloat().coerceIn(0f, h)

                // Grid at the top, middle and bottom.
                listOf(0f, h / 2, h).forEach { gy ->
                    drawLine(gridColor, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                }

                val predicted = points.mapIndexedNotNull { i, p -> p.predicted?.let { Offset(x(i), y(it)) } }
                if (predicted.size > 1) {
                    drawPath(
                        Path().apply {
                            moveTo(predicted[0].x, predicted[0].y)
                            predicted.drop(1).forEach { lineTo(it.x, it.y) }
                        },
                        predictedColor,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    )
                }

                val measured = points.mapIndexed { i, p -> Offset(x(i), y(p.recall)) }
                if (measured.size > 1) {
                    drawPath(
                        Path().apply {
                            moveTo(measured[0].x, measured[0].y)
                            measured.drop(1).forEach { lineTo(it.x, it.y) }
                        },
                        measuredColor,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
                measured.forEach { drawCircle(measuredColor, radius = 3.5.dp.toPx(), center = it) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 36.dp)) {
            Text(periodLabel(points.first().periodStart, period), style = labelStyle, color = labelColor)
            Spacer(Modifier.weight(1f))
            if (points.size > 1) {
                Text(periodLabel(points.last().periodStart, period), style = labelStyle, color = labelColor)
            }
        }
    }
}

/**
 * One dot per review, coloured by grade and placed at the predicted recall
 * just before it, over a dashed line of those predictions: a red dot high up
 * is a card forgotten when FSRS expected it to be remembered.
 */
@Composable
private fun CardHistoryChart(card: CardRetention) {
    val predictedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val gradeColors = gradeColors()
    val history = card.history
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val w = size.width
            val h = size.height
            val pad = 6.dp.toPx()
            fun x(i: Int) = if (history.size == 1) w / 2 else pad + (w - 2 * pad) * i / (history.size - 1)
            fun y(v: Double) = pad + ((h - 2 * pad) * (1 - v)).toFloat()

            drawLine(gridColor, Offset(0f, y(1.0)), Offset(w, y(1.0)), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, y(0.5)), Offset(w, y(0.5)), strokeWidth = 1f)

            val predicted = history.mapIndexedNotNull { i, p -> p.predicted?.let { Offset(x(i), y(it)) } }
            if (predicted.size > 1) {
                drawPath(
                    Path().apply {
                        moveTo(predicted[0].x, predicted[0].y)
                        predicted.drop(1).forEach { lineTo(it.x, it.y) }
                    },
                    predictedColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                )
            }
            history.forEachIndexed { i, p ->
                val color = gradeColors[p.grade.coerceIn(1, 4) - 1]
                drawCircle(color, radius = 4.5.dp.toPx(), center = Offset(x(i), y(p.predicted ?: 1.0)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatDateWithoutYear(history.first().reviewTime), style = labelStyle, color = labelColor)
            Spacer(Modifier.weight(1f))
            if (history.size > 1) {
                Text(formatDateWithoutYear(history.last().reviewTime), style = labelStyle, color = labelColor)
            }
        }
    }
}

@Composable
private fun TrendLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(MaterialTheme.colorScheme.primary, "Recall", dashed = false)
        LegendItem(MaterialTheme.colorScheme.tertiary, "Predicted", dashed = true)
    }
}

@Composable
private fun GradeLegend() {
    val colors = gradeColors()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf("Again", "Hard", "Good", "Easy").forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
        LegendItem(MaterialTheme.colorScheme.tertiary, "Predicted", dashed = true)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(18.dp).height(8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    )
}

/** Again, Hard, Good, Easy. */
@Composable
private fun gradeColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.error,
    Color(0xFFE0A030),
    Color(0xFF4CAF50),
    MaterialTheme.colorScheme.primary
)

@Composable
private fun retentionColor(value: Double?): Color = when {
    value == null -> MaterialTheme.colorScheme.outline
    value >= 0.85 -> Color(0xFF4CAF50)
    value >= 0.70 -> Color(0xFFE0A030)
    else -> MaterialTheme.colorScheme.error
}

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

private fun days(value: Double): String = when {
    value < 1.0 -> "<1 d"
    value < 10.0 -> value.toOneDecimal() + " d"
    else -> "${value.roundToInt()} d"
}

private fun periodLabel(start: Long, period: RetentionPeriod): String = when (period) {
    RetentionPeriod.WEEK -> "Wk of " + formatDateWithoutYear(start, padDay = false)
    RetentionPeriod.MONTH -> formatDate(start, padDay = false)
}

private fun stateLabel(state: String): String = when (state) {
    "NEW" -> "New"
    "LEARNING" -> "Learning"
    "REVIEW" -> "Review"
    "RELEARNING" -> "Relearning"
    else -> state
}
