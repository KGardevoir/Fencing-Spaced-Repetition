// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.viewmodel.ConceptSummary
import com.fencing.spacedrepetition.util.Time
import com.fencing.spacedrepetition.util.formatDate

/**
 * The learning concepts: what each is, which cards carry it, which card
 * groups that puts it in, and how far it has been learnt in each learning
 * context it is used in.
 *
 * Concepts are global -- one list for the whole collection -- while their
 * progress is kept per learning context, exactly like a card's: Global, and
 * one per learning group. A concept in two groups that learn in different
 * learning groups therefore shows two lines of progress.
 *
 * @param onSaveConcept creates the concept when its id is 0 and updates it
 *   otherwise; the set is the learning groups it should belong to afterwards
 *   (Global is the concept's own `inGlobal`). Cards are tagged from the card
 *   editor, which offers a concept only where it belongs.
 * A concept's progress is derived from the review history of the cards it
 * is on, so it is shown here but not edited: change the cards, and the
 * progress follows. [onRederiveAll] rebuilds every concept from history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConceptsScreen(
    concepts: List<ConceptSummary>,
    /** Whether Global schedules by concept; each learning group carries its own flag. */
    globalConceptScheduling: Boolean,
    onNavigateToLearningGroups: () -> Unit,
    learningGroups: List<LearningGroup>,
    error: String?,
    onSaveConcept: (Concept, Set<Long>) -> Unit,
    onDeleteConcept: (Concept) -> Unit,
    onRederiveAll: () -> Unit,
    onClearError: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var editing by remember { mutableStateOf<ConceptSummary?>(null) }
    var deleting by remember { mutableStateOf<ConceptSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmRederive by remember { mutableStateOf(false) }

    val shown = if (query.isBlank()) concepts else concepts.filter { summary ->
        summary.concept.name.contains(query.trim(), ignoreCase = true) ||
            summary.concept.description.contains(query.trim(), ignoreCase = true) ||
            summary.groups.any { it.name.contains(query.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning Concepts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmRederive = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rederive from card history")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = ConceptSummary(Concept(name = ""), emptySet(), emptyList(), emptyList()) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Concept")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Tag cards with the concepts they practise, from the card editor. A concept " +
                        "can be picked for a card learnt in a context it is available in. " +
                        "A concept's progress is derived from " +
                        "its cards' review history, so tagging a card brings its history along. " +
                        "Where concept scheduling is on, a " +
                        "tagged card is scheduled by its concepts: a session draws the concepts that " +
                        "are due and shows the cards covering the most of them, and each card's grade " +
                        "is passed on to its concepts. Progress is kept per learning group, like a " +
                        "card's. Everywhere else cards are scheduled one by one, as before.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onNavigateToLearningGroups, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(AppIcons.LearningGroup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Turn concept scheduling on per learning group (or Global)")
                }
            }
            if (concepts.size > 5) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search concepts or groups") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (concepts.isEmpty()) {
                item {
                    Text(
                        "No concepts yet. Add one with the + button, or from a card's editor.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(shown, key = { it.concept.id }) { summary ->
                ConceptRow(
                    summary = summary,
                    globalConceptScheduling = globalConceptScheduling,
                    learningGroups = learningGroups,
                    onEdit = { editing = summary },
                    onDelete = { deleting = summary }
                )
            }
            // Room for the last row's buttons above the action button.
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    editing?.let { summary ->
        // The live row where there is one, so a reset shows at once.
        ConceptEditDialog(
            summary = concepts.find { it.concept.id == summary.concept.id && summary.concept.id != 0L } ?: summary,
            globalConceptScheduling = globalConceptScheduling,
            concepts = concepts.map { it.concept },
            learningGroups = learningGroups,
            onSave = { updated, learningGroupIds ->
                onSaveConcept(updated, learningGroupIds)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { summary ->
        val cardCount = summary.cardIds.size
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Concept") },
            text = {
                Text(
                    "Delete \"${summary.concept.name}\"? Its learning progress will be lost" +
                        (if (cardCount > 0) ", and its $cardCount card${if (cardCount == 1) "" else "s"} " +
                            "will go back to being scheduled on their own" else "") +
                        ". Cards are not deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConcept(summary.concept)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmRederive) {
        AlertDialog(
            onDismissRequest = { confirmRederive = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("Rederive Concepts") },
            text = {
                Text(
                    "Rebuild every concept's progress from the review history of its cards. " +
                        "This already happens whenever cards, tags or groups change; a concept " +
                        "whose cards have no history goes back to new."
                )
            },
            confirmButton = {
                TextButton(onClick = { onRederiveAll(); confirmRederive = false }) { Text("Rederive") }
            },
            dismissButton = { TextButton(onClick = { confirmRederive = false }) { Text("Cancel") } }
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Could not save") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = onClearError) { Text("OK") } }
        )
    }
}

@Composable
private fun ConceptRow(
    summary: ConceptSummary,
    globalConceptScheduling: Boolean,
    learningGroups: List<LearningGroup>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val concept = summary.concept
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(concept.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (concept.description.isNotBlank()) {
                    Text(
                        concept.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Used by ${summary.cardIds.size} card${if (summary.cardIds.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                // Each place it is available, marked where concept scheduling is off.
                fun label(name: String, on: Boolean) = if (on) name else "$name (scheduling off)"
                val availableIn = buildList {
                    if (concept.inGlobal) add(label("Global", globalConceptScheduling))
                    learningGroups.filter { it.id in summary.learningGroupIds }.forEach { add(label(it.name, it.useConcepts)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.LearningGroup,
                        contentDescription = "Available in",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (availableIn.isEmpty()) "Available everywhere"
                        else "Available in " + availableIn.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.CardGroups,
                        contentDescription = "Groups",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (summary.groups.isEmpty()) "In no groups"
                        else summary.groups.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                summary.states.forEach { (learningGroup, state) ->
                    StateLine(learningGroup, state, learningGroup?.useConcepts ?: globalConceptScheduling)
                }
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${concept.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${concept.name}")
                }
            }
        }
    }
}

/** "Global · REVIEW · due 3 Dec" -- one learning context's progress, on one line. */
@Composable
private fun StateLine(learningGroup: LearningGroup?, state: ConceptLearningState, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(
            if (learningGroup == null) Icons.Default.Public else AppIcons.LearningGroup,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "${learningGroup?.name ?: "Global"} · " +
                if (enabled) describeState(state) else "concept scheduling off",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun describeState(state: ConceptLearningState): String = when {
    state.fsrsState == "NEW" && state.fsrsReps == 0 -> "new"
    state.nextReview <= Time.now() -> "${state.fsrsState.lowercase()} · due now"
    else -> "${state.fsrsState.lowercase()} · due ${formatDate(state.nextReview, padDay = false)}"
}

/**
 * A concept's name and description, where it is available -- the learning
 * contexts whose cards may be tagged with it -- and, for one that exists, its
 * progress.
 *
 * Every learning context can be chosen: tagging is always allowed. One whose
 * concept scheduling is off says so -- its cards can carry the concept, but
 * are scheduled card by card until the switch is turned on.
 */
@Composable
private fun ConceptEditDialog(
    summary: ConceptSummary,
    globalConceptScheduling: Boolean,
    concepts: List<Concept>,
    learningGroups: List<LearningGroup>,
    onSave: (Concept, Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val concept = summary.concept
    val isNew = concept.id == 0L
    var name by rememberSaveable { mutableStateOf(concept.name) }
    var description by rememberSaveable { mutableStateOf(concept.description) }
    var inGlobal by rememberSaveable { mutableStateOf(concept.inGlobal) }
    var checkedLearningGroups by remember { mutableStateOf(summary.learningGroupIds) }

    val nameTaken = concepts.any { it.id != concept.id && it.name.equals(name.trim(), ignoreCase = true) }
    fun save() {
        onSave(
            concept.copy(name = name.trim(), description = description.trim(), inGlobal = inGlobal),
            checkedLearningGroups.filter { id -> learningGroups.any { it.id == id } }.toSet()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
        title = { Text(if (isNew) "New Concept" else "Edit Concept") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = nameTaken,
                        supportingText = if (nameTaken) {
                            { Text("That name is already in use") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!isNew && summary.states.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Progress",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Derived from the review history of this concept's cards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Keys are per section: progress and availability rows are both
                    // per learning group, and share this list.
                    items(summary.states, key = { "progress-${it.first?.id ?: "global"}" }) { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                StateLine(entry.first, entry.second, entry.first?.useConcepts ?: globalConceptScheduling)
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Available in",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Cards learnt here can be tagged with this concept. Leave everything " +
                            "unticked to make it available everywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    AvailabilityRow("Global", Icons.Default.Public, inGlobal, schedulingOn = globalConceptScheduling) {
                        inGlobal = it
                    }
                }
                items(learningGroups, key = { "available-${it.id}" }) { learningGroup ->
                    AvailabilityRow(
                        learningGroup.name,
                        AppIcons.LearningGroup,
                        learningGroup.id in checkedLearningGroups,
                        schedulingOn = learningGroup.useConcepts
                    ) {
                        checkedLearningGroups =
                            if (it) checkedLearningGroups + learningGroup.id else checkedLearningGroups - learningGroup.id
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { save() },
                enabled = name.isNotBlank() && !nameTaken
            ) { Text(if (isNew) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AvailabilityRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    /** Whether this context schedules by concept; off is noted, not prevented. */
    schedulingOn: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (!schedulingOn) {
                Text(
                    "Concept scheduling is off here: tags are kept, cards are scheduled one by one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
