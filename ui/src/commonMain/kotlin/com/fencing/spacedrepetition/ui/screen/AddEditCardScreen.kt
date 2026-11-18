// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.Grade
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.components.LargeImageNotice
import com.fencing.spacedrepetition.ui.image.LARGE_IMAGE_BYTES
import com.fencing.spacedrepetition.ui.image.LocalImagePicker
import com.fencing.spacedrepetition.util.formatDate
import com.fencing.spacedrepetition.util.formatDateWithoutYear
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownKeyboardToolbar
import com.fencing.spacedrepetition.ui.components.rememberMarkdownToolbarState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun AddEditCardScreen(
    allGroups: List<Group>,
    cardGroups: List<Group>,
    learningStates: List<CardLearningState>,
    learningGroups: List<LearningGroup> = emptyList(),
    allConcepts: List<Concept> = emptyList(),
    cardConcepts: List<Concept> = emptyList(),
    /** How much of each of [cardConcepts] the card contains (1 when absent). */
    cardConceptWeights: Map<Long, Double> = emptyMap(),
    cardToEdit: Card? = null,
    initialGroupId: Long? = null,
    onComputeGrade: (cardId: Long, grade: Grade, groupId: Long?, onComputed: (Card) -> Unit) -> Unit,
    onResetCardState: (cardId: Long, resetGroupStates: Boolean, onDone: () -> Unit) -> Unit,
    onResetCardStateInGroup: (cardId: Long, groupId: Long, onDone: () -> Unit) -> Unit,
    /**
     * concepts maps each concept the card carries to how much of it the card
     * contains; null when the concepts were not touched, leaving them as they are.
     */
    onUpdateCard: (card: Card, groupIds: List<Long>, concepts: Map<Long, Double>?, onSuccess: () -> Unit) -> Unit,
    onRecordGradeFromEdit: (before: Card, after: Card, grade: Grade, groupId: Long?) -> Unit,
    onUpdateLearningState: (CardLearningState) -> Unit,
    onAddCard: (
        question: String,
        answer: String,
        groupIds: List<Long>,
        imagePaths: List<String>,
        concepts: Map<Long, Double>,
        onSuccess: () -> Unit
    ) -> Unit,
    onCreateGroup: (name: String, onCreated: (Long) -> Unit) -> Unit,
    /**
     * The card's learning contexts for the groups selected (learning group
     * ids, null for Global): where a concept created here is made available.
     */
    cardContextsFor: (groupIds: Set<Long>) -> Set<Long?> = { setOf(null) },
    /** Of [allConcepts], the ones available in some of those contexts. */
    offeredConceptIdsFor: (groupIds: Set<Long>) -> Set<Long> = { emptySet() },
    /**
     * A note when some of those contexts do not schedule by concept, or null.
     * Tagging is always allowed; the note says where the tags have no effect.
     */
    schedulingOffNoteFor: (groupIds: Set<Long>) -> String? = { null },
    onCreateConcept: (name: String, contexts: Set<Long?>, onCreated: (Long) -> Unit) -> Unit = { _, _, _ -> },
    onNavigateBack: () -> Unit
) {
    var question by rememberSaveable { mutableStateOf(cardToEdit?.question ?: "") }
    var answerFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(cardToEdit?.answer ?: ""))
    }
    var imagePaths by rememberSaveable { mutableStateOf<List<String>>(cardToEdit?.imagePaths?.toMutableList() ?: mutableListOf()) }

    var selectedGroupIds by remember(cardGroups, initialGroupId) {
        val initialIds = cardGroups.map { it.id }.toMutableSet()
        // For new cards, pre-select the initial group if provided
        if (cardToEdit == null && initialGroupId != null) {
            initialIds.add(initialGroupId)
        }
        mutableStateOf(initialIds.toSet())
    }

    // Learning concepts. Untouched until the user changes them, so a save
    // that races the concepts loading does not wipe them.
    var selectedConceptIds by remember(cardConcepts) { mutableStateOf(cardConcepts.map { it.id }.toSet()) }
    var conceptsTouched by rememberSaveable { mutableStateOf(false) }
    // How much of each concept the card contains; a concept not in it is 1.
    var conceptWeights by remember(cardConceptWeights) { mutableStateOf(cardConceptWeights) }
    var weighing by remember { mutableStateOf<Concept?>(null) }
    var removingConcept by remember { mutableStateOf<Concept?>(null) }
    fun selectedConcepts(): Map<Long, Double> = selectedConceptIds.associateWith { conceptWeights[it] ?: 1.0 }
    var showConceptSelectionSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateConceptDialog by rememberSaveable { mutableStateOf(false) }

    var showGroupSelectionSheet by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateGroupDialog by rememberSaveable { mutableStateOf(false) }
    var isDirty by rememberSaveable { mutableStateOf(false) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    val markdownToolbarState = rememberMarkdownToolbarState()

    val imagePicker = LocalImagePicker.current
    var lastLargeImageBytes by remember { mutableStateOf<Int?>(null) }

    // Mutable state for editing learning group states (learningGroupId -> state fields)
    var learningGroupEdits by remember(learningStates) {
        mutableStateOf(
            learningStates.associate { state ->
                state.learningGroupId to LearningGroupStateEdit(
                    fsrsStability = state.fsrsStability.toString(),
                    fsrsDifficulty = state.fsrsDifficulty.toString(),
                    fsrsReps = state.fsrsReps.toString(),
                    fsrsLapses = state.fsrsLapses.toString(),
                    fsrsState = state.fsrsState
                )
            }
        )
    }

    // Learning state fields (only for editing)
    var fsrsStability by rememberSaveable { mutableStateOf(cardToEdit?.fsrsStability?.toString() ?: "0.0") }
    var fsrsDifficulty by rememberSaveable { mutableStateOf(cardToEdit?.fsrsDifficulty?.toString() ?: "0.0") }
    var fsrsReps by rememberSaveable { mutableStateOf(cardToEdit?.fsrsReps?.toString() ?: "0") }
    var fsrsLapses by rememberSaveable { mutableStateOf(cardToEdit?.fsrsLapses?.toString() ?: "0") }
    var fsrsState by rememberSaveable { mutableStateOf(cardToEdit?.fsrsState ?: "NEW") }

    // Additional state for review timing (updated by quick grading)
    var lastReview by rememberSaveable { mutableStateOf(cardToEdit?.lastReview ?: 0L) }
    var nextReview by rememberSaveable { mutableStateOf(cardToEdit?.nextReview ?: 0L) }
    var fsrsElapsedDays by rememberSaveable { mutableStateOf(cardToEdit?.fsrsElapsedDays?.toString() ?: "0") }
    var fsrsScheduledDays by rememberSaveable { mutableStateOf(cardToEdit?.fsrsScheduledDays?.toString() ?: "0") }

    // Track the last grade applied via the grade buttons (null = none yet); used to log history on save
    var appliedGrade by rememberSaveable { mutableStateOf<Grade?>(null) }
    var appliedGradeGroupId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Track per-group next review dates for instant UI updates after grading
    var groupNextReviews by remember(learningStates) {
        mutableStateOf(
            learningStates.associate { it.learningGroupId to it.nextReview }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The learning groups this card is learnt in through its selected groups,
    // each with one of those groups: grading uses that group's settings, and
    // resetting goes through it to the learning group's state.
    val cardLearningGroups: List<Pair<LearningGroup, Group>> = learningGroups.mapNotNull { learningGroup ->
        allGroups.firstOrNull { it.learningGroupId == learningGroup.id && it.id in selectedGroupIds }
            ?.let { learningGroup to it }
    }

    val isEditing = cardToEdit != null

    BackHandler(enabled = isDirty) {
        showUnsavedChangesDialog = true
    }

    // Saves from the top bar, as every editor does.
    fun save() {
        if (question.isNotBlank()) {
            if (isEditing && cardToEdit != null) {
                // Build updated card with learning state if advanced settings were used
                val updatedCard = cardToEdit.copy(
                    question = question,
                    answer = answerFieldValue.text,
                    imagePaths = imagePaths,
                    // FSRS state
                    fsrsStability = fsrsStability.toDoubleOrNull() ?: cardToEdit.fsrsStability,
                    fsrsDifficulty = fsrsDifficulty.toDoubleOrNull() ?: cardToEdit.fsrsDifficulty,
                    fsrsReps = fsrsReps.toIntOrNull() ?: cardToEdit.fsrsReps,
                    fsrsLapses = fsrsLapses.toIntOrNull() ?: cardToEdit.fsrsLapses,
                    fsrsState = fsrsState,
                    fsrsElapsedDays = fsrsElapsedDays.toIntOrNull() ?: cardToEdit.fsrsElapsedDays,
                    fsrsScheduledDays = fsrsScheduledDays.toIntOrNull() ?: cardToEdit.fsrsScheduledDays,
                    // Review timing
                    lastReview = lastReview,
                    nextReview = nextReview
                )
                onUpdateCard(
                    updatedCard,
                    selectedGroupIds.toList(),
                    if (conceptsTouched) selectedConcepts() else null
                ) {
                    // Record a history entry if a grade was applied via the grade buttons
                    val grade = appliedGrade
                    if (grade != null && cardToEdit != null) {
                        onRecordGradeFromEdit(cardToEdit, updatedCard, grade, appliedGradeGroupId)
                    }
                    // Save independent learning state changes
                    learningGroupEdits.forEach { (learningGroupId, edit) ->
                        val existingState = learningStates.find { it.learningGroupId == learningGroupId }
                        if (existingState != null) {
                            val updatedState = existingState.copy(
                                fsrsStability = edit.fsrsStability.toDoubleOrNull() ?: existingState.fsrsStability,
                                fsrsDifficulty = edit.fsrsDifficulty.toDoubleOrNull() ?: existingState.fsrsDifficulty,
                                fsrsReps = edit.fsrsReps.toIntOrNull() ?: existingState.fsrsReps,
                                fsrsLapses = edit.fsrsLapses.toIntOrNull() ?: existingState.fsrsLapses,
                                fsrsState = edit.fsrsState
                            )
                            onUpdateLearningState(updatedState)
                        }
                    }
                    isDirty = false
                    onNavigateBack()
                }
            } else {
                onAddCard(
                    question,
                    answerFieldValue.text,
                    selectedGroupIds.toList(),
                    imagePaths,
                    selectedConcepts()
                ) { isDirty = false; onNavigateBack() }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Card" else "Add New Card") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) {
                            showUnsavedChangesDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = question.isNotBlank()) {
                        Text(if (isEditing) "Save" else "Add")
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
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Question input
            OutlinedTextField(
                value = question,
                onValueChange = { question = it; isDirty = true },
                label = { Text("Concept") },
                placeholder = { Text("e.g., En garde position") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.QuestionMark, contentDescription = null)
                },
                minLines = 2,
                maxLines = 4
            )

            // Answer input with markdown toolbar and preview toggle
            MarkdownDescriptionField(
                value = answerFieldValue,
                onValueChange = { answerFieldValue = it; isDirty = true },
                modifier = Modifier.fillMaxWidth(),
                toolbarState = markdownToolbarState
            )

            // Images section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Images", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                imagePicker.pick { picked ->
                                    imagePaths = (imagePaths + picked.key).toMutableList()
                                    isDirty = true
                                    lastLargeImageBytes =
                                        picked.byteCount.takeIf { it > LARGE_IMAGE_BYTES }
                                }
                            },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Image")
                        }
                    }

                    if (imagePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CardImagesEdit(
                            imagePaths = imagePaths,
                            onRemoveImage = { path ->
                                imagePaths = imagePaths.filter { it != path }.toMutableList()
                                isDirty = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        LargeImageNotice(lastLargeImageBytes)
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No images added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Groups selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showGroupSelectionSheet = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.CardGroups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Groups", style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit groups",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedGroupIds.isEmpty()) {
                        Text(
                            "No groups selected (tap to add)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allGroups.filter { it.id in selectedGroupIds }.forEach { group ->
                                AssistChip(
                                    onClick = {
                                        selectedGroupIds = selectedGroupIds - group.id
                                        isDirty = true
                                    },
                                    label = { Text(group.name) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Learning concepts
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showConceptSelectionSheet = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Concepts", style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit concepts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedConceptIds.isEmpty()) {
                        Text(
                            "No concepts (tap to add). Without concepts the card is always scheduled on its own.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val schedulingOffNote = schedulingOffNoteFor(selectedGroupIds)
                        if (schedulingOffNote != null) {
                            SchedulingOffNote(schedulingOffNote)
                        } else {
                            Text(
                                "This card is scheduled by these concepts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allConcepts.filter { it.id in selectedConceptIds }.forEach { concept ->
                                // Tap to set how much of the concept the card contains;
                                // long-press to take it off the card.
                                ConceptWeightChip(
                                    label = conceptChipLabel(concept.name, conceptWeights[concept.id] ?: 1.0),
                                    name = concept.name,
                                    onClick = { weighing = concept },
                                    onLongClick = { removingConcept = concept }
                                )
                            }
                        }
                        Text(
                            "Tap a concept to set how much of it the card has; long-press to remove it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Next review schedule (only when editing)
            if (isEditing && cardToEdit != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Review Schedule",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Global next review
                        val globalStatusText = formatReviewStatus(fsrsState, nextReview)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Global",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                globalStatusText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    fsrsState == "NEW" && nextReview == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                                    nextReview <= Time.now() -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                        }

                        // Per-group next review dates
                        cardLearningGroups.forEach { (group, _) ->
                            val groupReview = groupNextReviews[group.id] ?: 0L
                            val groupState = learningStates.find { it.learningGroupId == group.id }
                            val groupFsrsState = groupState?.fsrsState ?: "NEW"
                            val groupStatusText = formatReviewStatus(groupFsrsState, groupReview)

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    group.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    groupStatusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        groupFsrsState == "NEW" && groupReview == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                                        groupReview <= Time.now() -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Grade section (only when editing)
            if (isEditing && cardToEdit != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var showGradeButtons by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGradeButtons = !showGradeButtons },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Quick Grade",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                if (showGradeButtons) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showGradeButtons) "Collapse" else "Expand"
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Grade this card without a practice session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (showGradeButtons) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            // Global state grading
                            Text(
                                "Global State",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(Grade.AGAIN, Grade.HARD, Grade.GOOD, Grade.EASY).forEach { grade ->
                                    CompactGradeButton(
                                        grade = grade,
                                        onClick = {
                                            onComputeGrade(cardToEdit.id, grade, null) { updated ->
                                                fsrsStability = updated.fsrsStability.toString()
                                                fsrsDifficulty = updated.fsrsDifficulty.toString()
                                                fsrsReps = updated.fsrsReps.toString()
                                                fsrsLapses = updated.fsrsLapses.toString()
                                                fsrsState = updated.fsrsState
                                                fsrsElapsedDays = updated.fsrsElapsedDays.toString()
                                                fsrsScheduledDays = updated.fsrsScheduledDays.toString()
                                                lastReview = updated.lastReview
                                                nextReview = updated.nextReview
                                                appliedGrade = grade
                                                appliedGradeGroupId = null
                                                isDirty = true
                                                scope.launch {
                                                    val dateStr = formatDateWithoutYear(updated.nextReview)
                                                    snackbarHostState.showSnackbar(
                                                        "Grade staged — save to apply (next: $dateStr)"
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Learning group grading
                            if (cardLearningGroups.isNotEmpty()) {
                                cardLearningGroups.forEach { (learningGroup, group) ->
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            AppIcons.LearningGroup,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "${learningGroup.name} (Learning Group)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(Grade.AGAIN, Grade.HARD, Grade.GOOD, Grade.EASY).forEach { grade ->
                                            CompactGradeButton(
                                                grade = grade,
                                                onClick = {
                                                    onComputeGrade(cardToEdit.id, grade, group.id) { updated ->
                                                        learningGroupEdits = learningGroupEdits + (learningGroup.id to LearningGroupStateEdit(
                                                            fsrsStability = updated.fsrsStability.toString(),
                                                            fsrsDifficulty = updated.fsrsDifficulty.toString(),
                                                            fsrsReps = updated.fsrsReps.toString(),
                                                            fsrsLapses = updated.fsrsLapses.toString(),
                                                            fsrsState = updated.fsrsState
                                                        ))
                                                        groupNextReviews = groupNextReviews + (learningGroup.id to updated.nextReview)
                                                        appliedGrade = grade
                                                        appliedGradeGroupId = group.id
                                                        isDirty = true
                                                        scope.launch {
                                                            val dateStr = formatDateWithoutYear(updated.nextReview)
                                                            snackbarHostState.showSnackbar(
                                                                "${learningGroup.name}: Grade staged — save to apply (next: $dateStr)"
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Advanced settings (learning state) - only show when editing
            if (isEditing && cardToEdit != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedSettings = !showAdvancedSettings },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Learning State",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Icon(
                                if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showAdvancedSettings) "Collapse" else "Expand"
                            )
                        }

                        // Card status summary
                        val statusText = when {
                            fsrsState == "NEW" && nextReview == 0L -> "New card"
                            nextReview <= Time.now() -> "Due for review"
                            else -> {
                                "Next review: ${formatDate(nextReview)}"
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (showAdvancedSettings) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            // Reset state button
                            OutlinedButton(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset Learning State")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // FSRS Parameters
                            Text(
                                "FSRS Parameters",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // State dropdown
                            var stateExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = stateExpanded,
                                onExpandedChange = { stateExpanded = !stateExpanded }
                            ) {
                                OutlinedTextField(
                                    value = fsrsState,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("State") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = stateExpanded,
                                    onDismissRequest = { stateExpanded = false }
                                ) {
                                    listOf("NEW", "LEARNING", "REVIEW", "RELEARNING").forEach { state ->
                                        DropdownMenuItem(
                                            text = { Text(state) },
                                            onClick = {
                                                fsrsState = state
                                                stateExpanded = false
                                                isDirty = true
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = fsrsStability,
                                    onValueChange = { fsrsStability = it; isDirty = true },
                                    label = { Text("Stability") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = fsrsDifficulty,
                                    onValueChange = { fsrsDifficulty = it; isDirty = true },
                                    label = { Text("Difficulty") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = fsrsReps,
                                    onValueChange = { fsrsReps = it; isDirty = true },
                                    label = { Text("Reps") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = fsrsLapses,
                                    onValueChange = { fsrsLapses = it; isDirty = true },
                                    label = { Text("Lapses") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }


                        }
                    }
                }

                // Learning group states section
                if (cardLearningGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    cardLearningGroups.forEach { (learningGroup, group) ->
                        val groupState = learningStates.find { it.learningGroupId == learningGroup.id }
                        val editState = learningGroupEdits[learningGroup.id]

                        LearningGroupStateCard(
                            groupName = learningGroup.name,
                            groupState = groupState,
                            editState = editState,
                            onStateChange = { newEdit ->
                                learningGroupEdits = learningGroupEdits + (learningGroup.id to newEdit)
                                isDirty = true
                            },
                            onReset = {
                                cardToEdit?.let { card ->
                                    onResetCardStateInGroup(card.id, group.id) {
                                        // Reset local edit state
                                        learningGroupEdits = learningGroupEdits + (learningGroup.id to LearningGroupStateEdit())
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

        } // end scrollable Column
        // Markdown toolbar pinned above the keyboard
        MarkdownKeyboardToolbar(markdownToolbarState)
    } // end outer Column
    }

    // Group selection bottom sheet
    if (showGroupSelectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGroupSelectionSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Select Groups",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (allGroups.isEmpty()) {
                    Text(
                        "No groups available. Create groups from the home screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        items(allGroups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGroupIds = if (group.id in selectedGroupIds) {
                                            selectedGroupIds - group.id
                                        } else {
                                            selectedGroupIds + group.id
                                        }
                                        isDirty = true
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = group.id in selectedGroupIds,
                                    onCheckedChange = { checked ->
                                        selectedGroupIds = if (checked) {
                                            selectedGroupIds + group.id
                                        } else {
                                            selectedGroupIds - group.id
                                        }
                                        isDirty = true
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(group.name, style = MaterialTheme.typography.bodyLarge)
                                    if (group.description.isNotEmpty()) {
                                        Text(
                                            group.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showCreateGroupDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Group")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showGroupSelectionSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Reset learning state confirmation dialog
    if (showResetDialog && cardToEdit != null) {
        val resetLearningGroups = cardLearningGroups

        if (resetLearningGroups.isNotEmpty()) {
            // Show enhanced dialog with options
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                title = { Text("Reset Learning State") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "This card is learnt in ${resetLearningGroups.size} learning group(s) as well as Global.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Choose which state(s) to reset:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Reset Global State Only
                        Button(
                            onClick = {
                                onResetCardState(cardToEdit.id, false) {
                                    // Reset local global state variables
                                    fsrsStability = "0.0"
                                    fsrsDifficulty = "0.0"
                                    fsrsReps = "0"
                                    fsrsLapses = "0"
                                    fsrsState = "NEW"
                                }
                                showResetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset Global State Only")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reset Group States Only
                        OutlinedButton(
                            onClick = {
                                resetLearningGroups.forEach { (learningGroup, group) ->
                                    onResetCardStateInGroup(cardToEdit.id, group.id) {
                                        // Reset local edit state for this learning group
                                        learningGroupEdits = learningGroupEdits + (learningGroup.id to LearningGroupStateEdit())
                                    }
                                }
                                showResetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset Learning Group States Only (${resetLearningGroups.size})")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reset Both
                        OutlinedButton(
                            onClick = {
                                onResetCardState(cardToEdit.id, true) {
                                    // Reset local global state
                                    fsrsStability = "0.0"
                                    fsrsDifficulty = "0.0"
                                    fsrsReps = "0"
                                    fsrsLapses = "0"
                                    fsrsState = "NEW"
                                }
                                // Reset local edit states for all groups
                                resetLearningGroups.forEach { (learningGroup, _) ->
                                    learningGroupEdits = learningGroupEdits + (learningGroup.id to LearningGroupStateEdit())
                                }
                                showResetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset Both Global & Learning Group States")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        } else {
            // Show simple dialog for cards not in independent learning groups
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                title = { Text("Reset Learning State?") },
                text = { Text("This will reset all learning progress for this card. The card will be treated as new and all FSRS data will be cleared.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onResetCardState(cardToEdit.id, false) {
                                // Reset local state variables too
                                fsrsStability = "0.0"
                                fsrsDifficulty = "0.0"
                                fsrsReps = "0"
                                fsrsLapses = "0"
                                fsrsState = "NEW"
                            }
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // How much of one concept the card contains, relative to its others
    weighing?.let { concept ->
        var text by remember(concept.id) { mutableStateOf(formatWeight(conceptWeights[concept.id] ?: 1.0)) }
        val entered = text.trim().replace(',', '.').toDoubleOrNull()
        val valid = entered != null && entered >= 0.0 && !entered.isInfinite()
        // The share of the card this would make the concept, against its others as they stand.
        val others = selectedConceptIds.filter { it != concept.id }.sumOf { conceptWeights[it] ?: 1.0 }
        val share = if (valid && entered!! + others > 0.0) entered / (entered + others) else null
        AlertDialog(
            onDismissRequest = { weighing = null },
            icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
            title = { Text(concept.name) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("How much of this concept") },
                        singleLine = true,
                        isError = !valid,
                        supportingText = {
                            Text(
                                when {
                                    !valid -> "Enter a number, 0 or more"
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
                TextButton(
                    onClick = {
                        val weight = entered ?: return@TextButton
                        conceptWeights = if (weight == 1.0) conceptWeights - concept.id else conceptWeights + (concept.id to weight)
                        conceptsTouched = true
                        isDirty = true
                        weighing = null
                    },
                    enabled = valid
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { weighing = null }) { Text("Cancel") }
            }
        )
    }

    // Taking a concept off the card, from a long-press on its chip
    removingConcept?.let { concept ->
        AlertDialog(
            onDismissRequest = { removingConcept = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Remove Concept") },
            text = { Text("Take \"${concept.name}\" off this card? The concept itself is kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedConceptIds = selectedConceptIds - concept.id
                        conceptWeights = conceptWeights - concept.id
                        conceptsTouched = true
                        isDirty = true
                        removingConcept = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removingConcept = null }) { Text("Cancel") }
            }
        )
    }

    // Concept selection bottom sheet
    if (showConceptSelectionSheet) {
        var conceptQuery by rememberSaveable { mutableStateOf("") }
        // Offered: concepts available where this card is learnt. A concept the
        // card already carries is listed too, so it can be taken off.
        val offered = offeredConceptIdsFor(selectedGroupIds)
        val schedulingOffNote = schedulingOffNoteFor(selectedGroupIds)
        val pickable = allConcepts.filter { it.id in offered || it.id in selectedConceptIds }
        ModalBottomSheet(onDismissRequest = { showConceptSelectionSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Select Concepts", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                if (pickable.size > 8) {
                    OutlinedTextField(
                        value = conceptQuery,
                        onValueChange = { conceptQuery = it },
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
                    val listed = pickable.filter {
                        conceptQuery.isBlank() || it.name.contains(conceptQuery.trim(), ignoreCase = true)
                    }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        items(listed, key = { it.id }) { concept ->
                            val checked = concept.id in selectedConceptIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedConceptIds =
                                            if (checked) selectedConceptIds - concept.id
                                            else selectedConceptIds + concept.id
                                        conceptsTouched = true
                                        isDirty = true
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        selectedConceptIds =
                                            if (on) selectedConceptIds + concept.id
                                            else selectedConceptIds - concept.id
                                        conceptsTouched = true
                                        isDirty = true
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(concept.name, style = MaterialTheme.typography.bodyLarge)
                                    if (concept.id !in offered) {
                                        Text(
                                            "Not available in this card's learning groups",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else if (concept.description.isNotEmpty()) {
                                        Text(
                                            concept.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showCreateConceptDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Concept")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showConceptSelectionSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Done") }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Create new concept dialog
    if (showCreateConceptDialog) {
        var newConceptName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateConceptDialog = false },
            icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
            title = { Text("Create New Concept") },
            text = {
                OutlinedTextField(
                    value = newConceptName,
                    onValueChange = { newConceptName = it },
                    label = { Text("Concept Name") },
                    supportingText = {
                        Text("Made available in this card's learning groups. An existing concept of the same name is reused.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateConcept(newConceptName, cardContextsFor(selectedGroupIds)) { newId ->
                            selectedConceptIds = selectedConceptIds + newId
                            conceptsTouched = true
                            isDirty = true
                            showCreateConceptDialog = false
                        }
                    },
                    enabled = newConceptName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateConceptDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create new group dialog
    if (showCreateGroupDialog) {
        var newGroupName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            icon = { Icon(AppIcons.CardGroups, contentDescription = null) },
            title = { Text("Create New Group") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            onCreateGroup(newGroupName) { newId ->
                                selectedGroupIds = selectedGroupIds + newId
                                showCreateGroupDialog = false
                            }
                        }
                    },
                    enabled = newGroupName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unsaved changes warning dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Leaving now will discard them.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedChangesDialog = false
                        isDirty = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningGroupStateCard(
    groupName: String,
    groupState: CardLearningState?,
    editState: LearningGroupStateEdit?,
    onStateChange: (LearningGroupStateEdit) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Use edit state if available, otherwise use group state or defaults
    val currentEdit = editState ?: LearningGroupStateEdit(
        fsrsStability = groupState?.fsrsStability?.toString() ?: "0.0",
        fsrsDifficulty = groupState?.fsrsDifficulty?.toString() ?: "0.0",
        fsrsReps = groupState?.fsrsReps?.toString() ?: "0",
        fsrsLapses = groupState?.fsrsLapses?.toString() ?: "0",
        fsrsState = groupState?.fsrsState ?: "NEW"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.LearningGroup,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "$groupName (Independent)",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            // Status summary
            val statusText = if (groupState == null) {
                "No state yet"
            } else if (groupState.fsrsState == "NEW" && groupState.nextReview == 0L) {
                "New card"
            } else if (groupState.nextReview <= Time.now()) {
                "Due for review"
            } else {
                "Next: ${formatDate(groupState.nextReview)}"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Reset button
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset State", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // FSRS Parameters
                Text(
                    "FSRS Parameters",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // State dropdown
                var stateExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = stateExpanded,
                    onExpandedChange = { stateExpanded = !stateExpanded }
                ) {
                    OutlinedTextField(
                        value = currentEdit.fsrsState,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("State") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(
                        expanded = stateExpanded,
                        onDismissRequest = { stateExpanded = false }
                    ) {
                        listOf("NEW", "LEARNING", "REVIEW", "RELEARNING").forEach { state ->
                            DropdownMenuItem(
                                text = { Text(state) },
                                onClick = {
                                    onStateChange(currentEdit.copy(fsrsState = state))
                                    stateExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentEdit.fsrsStability,
                        onValueChange = { onStateChange(currentEdit.copy(fsrsStability = it)) },
                        label = { Text("Stability") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = currentEdit.fsrsDifficulty,
                        onValueChange = { onStateChange(currentEdit.copy(fsrsDifficulty = it)) },
                        label = { Text("Difficulty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = currentEdit.fsrsReps,
                        onValueChange = { onStateChange(currentEdit.copy(fsrsReps = it)) },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = currentEdit.fsrsLapses,
                        onValueChange = { onStateChange(currentEdit.copy(fsrsLapses = it)) },
                        label = { Text("Lapses") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }


            }
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            title = { Text("Reset $groupName State?") },
            text = { Text("This will reset the learning progress for this card in the $groupName group only.") },
            confirmButton = {
                Button(
                    onClick = {
                        onReset()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = grade.label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * Data class to hold editable independent learning state fields
 */
data class LearningGroupStateEdit(
    val fsrsStability: String = "0.0",
    val fsrsDifficulty: String = "0.0",
    val fsrsReps: String = "0",
    val fsrsLapses: String = "0",
    val fsrsState: String = "NEW"
)

/** Formats the next-review status for display. */
private fun formatReviewStatus(fsrsState: String, nextReview: Long): String = when {
    fsrsState == "NEW" && nextReview == 0L -> "New card"
    nextReview <= Time.now() -> "Due now"
    else -> formatDate(nextReview)
}

/** Says where concept scheduling is off: tagging still works, the tags just have no effect there. */
@Composable
private fun SchedulingOffNote(text: String) {
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

/** A concept chip's text: its name, and its weight when that is not the default 1. */
/** A concept on the card: tap to weigh it, long-press to remove it. Looks like an assist chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConceptWeightChip(label: String, name: String, onClick: () -> Unit, onLongClick: () -> Unit) {
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
