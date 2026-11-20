// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.FsrsState
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.util.Time
import com.fencing.spacedrepetition.util.formatDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A section's heading: an icon and a title, with [trailing] at the far end. */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        trailing()
    }
}

/** One of the card editor's sections: a card with a heading, tappable as a whole when [onClick] is set. */
@Composable
internal fun EditorSection(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(),
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val modifier = Modifier.fillMaxWidth()
    Card(modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier, colors = colors) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(icon, title, iconTint, trailing = trailing)
            content()
        }
    }
}

/** A section that opens and closes from its heading, with [summary] shown either way. */
@Composable
internal fun ExpandableSection(
    icon: ImageVector,
    title: String,
    summary: String,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(
                icon, title, MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** A context's progress in brief: its FSRS state, stability and difficulty. */
internal fun progressSummary(fsrs: FsrsState): String =
    "${fsrs.state} · stability ${formatWeight(fsrs.stability)} · difficulty ${formatWeight(fsrs.difficulty)}"

/**
 * One learning context's progress, editable -- the same for Global and every
 * learning group: Reset, then the FSRS state and its numbers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressFields(edit: ProgressEdit, onChange: (ProgressEdit) -> Unit, onReset: () -> Unit) {
    OutlinedButton(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Reset Learning State")
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text("FSRS Parameters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))

    var stateExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = stateExpanded, onExpandedChange = { stateExpanded = !stateExpanded }) {
        OutlinedTextField(
            value = edit.fsrs.state,
            onValueChange = {},
            readOnly = true,
            label = { Text("State") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
            listOf("NEW", "LEARNING", "REVIEW", "RELEARNING").forEach { state ->
                DropdownMenuItem(
                    text = { Text(state) },
                    onClick = {
                        onChange(edit.copy(fsrs = edit.fsrs.copy(state = state)))
                        stateExpanded = false
                    }
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(edit.stability, "Stability", KeyboardType.Decimal) { onChange(edit.copy(stability = it)) }
        NumberField(edit.difficulty, "Difficulty", KeyboardType.Decimal) { onChange(edit.copy(difficulty = it)) }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(edit.reps, "Reps", KeyboardType.Number) { onChange(edit.copy(reps = it)) }
        NumberField(edit.lapses, "Lapses", KeyboardType.Number) { onChange(edit.copy(lapses = it)) }
    }
}

@Composable
private fun RowScope.NumberField(value: String, label: String, keyboardType: KeyboardType, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.weight(1f),
        singleLine = true
    )
}

/** A context's name and when the card is next due there. */
@Composable
internal fun ReviewStatusRow(name: String, fsrs: FsrsState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            formatReviewStatus(fsrs.state, fsrs.nextReview),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                fsrs.state == "NEW" && fsrs.nextReview == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                fsrs.nextReview <= Time.now() -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.tertiary
            }
        )
    }
}

/** Formats the next-review status for display. */
internal fun formatReviewStatus(fsrsState: String, nextReview: Long): String = when {
    fsrsState == "NEW" && nextReview == 0L -> "New card"
    nextReview <= Time.now() -> "Due now"
    else -> formatDate(nextReview)
}

/** The four grades as a row of buttons. */
@Composable
internal fun GradeButtonRow(onGrade: (Grade) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Grade.AGAIN, Grade.HARD, Grade.GOOD, Grade.EASY).forEach { grade ->
            CompactGradeButton(grade = grade, onClick = { onGrade(grade) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun CompactGradeButton(
    grade: Grade,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (grade) {
        Grade.SKIP -> Pair(MaterialTheme.colorScheme.outline, Icons.Default.SkipNext)
        Grade.AGAIN -> Pair(MaterialTheme.colorScheme.error, Icons.Default.Close)
        Grade.HARD -> Pair(Color(0xFFFF9800), Icons.Default.Remove)
        Grade.GOOD -> Pair(Color(0xFF4CAF50), Icons.Default.Check)
        Grade.EASY -> Pair(Color(0xFF2196F3), Icons.Default.Done)
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = grade.label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/**
 * Confirms a reset. With [learningGroupCount] above 0 -- resetting Global on
 * a card also learnt in learning groups -- it offers Global, those, or both.
 */
@Composable
internal fun ResetProgressDialog(
    title: String,
    text: String,
    learningGroupCount: Int,
    onReset: (global: Boolean, learningGroups: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val danger = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (learningGroupCount == 0) {
                Button(
                    onClick = { onReset(true, false) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset") }
            } else {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onReset(true, false) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reset Global State Only") }
                    OutlinedButton(onClick = { onReset(false, true) }, modifier = Modifier.fillMaxWidth(), colors = danger) {
                        Text("Reset Learning Group States Only ($learningGroupCount)")
                    }
                    OutlinedButton(onClick = { onReset(true, true) }, modifier = Modifier.fillMaxWidth(), colors = danger) {
                        Text("Reset Both Global & Learning Group States")
                    }
                    // Here rather than as the dialog's dismiss button, which
                    // would be laid over a full-width column of buttons.
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
                }
            }
        },
        dismissButton = if (learningGroupCount == 0) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else null
    )
}

/** Asks for a name, for a new group or concept; [onCreate] gets it. */
@Composable
internal fun NameDialog(
    icon: ImageVector,
    title: String,
    label: String,
    supportingText: String? = null,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                supportingText = supportingText?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A row in a picker sheet: a checkbox, a name, and a line under it. */
@Composable
private fun PickerRow(checked: Boolean, name: String, note: String?, noteIsError: Boolean = false, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (!note.isNullOrEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (noteIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A bottom sheet to tick things on and off, with a button to create one and Done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    createLabel: String,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            content()
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(createLabel)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun GroupPickerSheet(
    allGroups: List<Group>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    PickerSheet("Select Groups", "Create New Group", onCreate, onDismiss) {
        if (allGroups.isEmpty()) {
            Text(
                "No groups available. Create groups from the home screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                items(allGroups, key = { it.id }) { group ->
                    PickerRow(group.id in selected, group.name, group.description) { onToggle(group.id) }
                }
            }
        }
    }
}

/**
 * The concepts to tag the card with: those [offered] where it is learnt,
 * and any it already carries, so they can be taken off.
 */
@Composable
internal fun ConceptPickerSheet(
    allConcepts: List<Concept>,
    selected: Set<Long>,
    offered: Set<Long>,
    schedulingOffNote: String?,
    onToggle: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val pickable = allConcepts.filter { it.id in offered || it.id in selected }
    PickerSheet("Select Concepts", "Create New Concept", onCreate, onDismiss) {
        if (pickable.size > 8) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search concepts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        schedulingOffNote?.let {
            SchedulingOffNote(it)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (pickable.isEmpty()) {
            Text(
                "No concepts are available in this card's learning groups. Create one " +
                    "below, or make one available from the Concepts screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val listed = pickable.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                items(listed, key = { it.id }) { concept ->
                    val notOffered = concept.id !in offered
                    PickerRow(
                        checked = concept.id in selected,
                        name = concept.name,
                        note = if (notOffered) "Not available in this card's learning groups" else concept.description,
                        noteIsError = notOffered
                    ) { onToggle(concept.id) }
                }
            }
        }
    }
}

/**
 * How much of [concept] the card contains, relative to its other concepts,
 * which weigh [others] together.
 */
@Composable
internal fun ConceptWeightDialog(concept: Concept, weight: Double, others: Double, onSet: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember(concept.id) { mutableStateOf(formatWeight(weight)) }
    val entered = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && !it.isInfinite() }
    // The share of the card this would make the concept, against its others as they stand.
    val share = entered?.let { if (it + others > 0.0) it / (it + others) else null }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
        title = { Text(concept.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("How much of this concept") },
                    singleLine = true,
                    isError = entered == null,
                    supportingText = {
                        Text(
                            when {
                                entered == null -> "Enter a number, 0 or more"
                                share != null -> "${(share * 100).roundToInt()}% of this card"
                                else -> "This card contains none of its concepts"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Any amount, relative to the card's other concepts: 2 here and 1 on another " +
                        "makes the card two-thirds this one. Grading a session weighs each card's " +
                        "grade by its share of the concept, and drawing by concept prefers cards " +
                        "with more of what is due. 0 means the card does not count towards it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { entered?.let(onSet) }, enabled = entered != null) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Taking a concept off the card, from a long-press on its chip. */
@Composable
internal fun RemoveConceptDialog(concept: Concept, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text("Remove Concept") },
        text = { Text("Take \"${concept.name}\" off this card? The concept itself is kept.") },
        confirmButton = {
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Says where concept scheduling is off: tagging still works, the tags just have no effect there. */
@Composable
internal fun SchedulingOffNote(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** A concept on the card: tap to weigh it, long-press to remove it. Looks like an assist chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConceptWeightChip(label: String, name: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .combinedClickable(
                onClickLabel = "Set how much of $name",
                onLongClickLabel = "Remove $name",
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** A concept chip's text: its name, and its weight when that is not the default 1. */
internal fun conceptChipLabel(name: String, weight: Double): String =
    if (weight == 1.0) name else "$name ×${formatWeight(weight)}"

/** A weight as people write it: "2", "0.5", "1.25" -- no trailing ".0". */
internal fun formatWeight(weight: Double): String {
    val hundredths = (weight * 100).roundToLong()
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toInt()
    return when {
        fraction == 0 -> whole.toString()
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}
