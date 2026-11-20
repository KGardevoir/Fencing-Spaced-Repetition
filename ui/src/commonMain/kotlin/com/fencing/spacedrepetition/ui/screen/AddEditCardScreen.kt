// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.FsrsState
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.fsrs
import com.fencing.spacedrepetition.data.model.withFsrs
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.LargeImageNotice
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownKeyboardToolbar
import com.fencing.spacedrepetition.ui.components.rememberLeaveGuard
import com.fencing.spacedrepetition.ui.components.rememberMarkdownToolbarState
import com.fencing.spacedrepetition.ui.image.LARGE_IMAGE_BYTES
import com.fencing.spacedrepetition.ui.image.LocalImagePicker
import com.fencing.spacedrepetition.util.formatDateWithoutYear
import kotlinx.coroutines.launch

/**
 * Adds a card, or edits one: its content, groups and concepts and -- when
 * editing -- its progress in each learning context it is learnt in (Global,
 * and each learning group one of its groups is in), with Quick Grade and a
 * reset for each. Everything but a reset is held in [CardEditorState] until
 * Save; a reset is saved at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    /** Resets the card's Global progress, and every learning group's too when asked; gives the card as reset. */
    onResetCardState: (cardId: Long, resetGroupStates: Boolean, onDone: (Card) -> Unit) -> Unit,
    onResetCardStateInGroup: (cardId: Long, groupId: Long, onDone: () -> Unit) -> Unit,
    /**
     * groupIds and concepts (each concept the card carries, with how much of
     * it the card contains) are null when untouched, leaving them as they are.
     */
    onUpdateCard: (card: Card, groupIds: List<Long>?, concepts: Map<Long, Double>?, onSuccess: () -> Unit) -> Unit,
    /** Logs a grade from Quick Grade: the context's progress before and after, and the group it went through. */
    onRecordGradeFromEdit: (cardId: Long, before: FsrsState, after: FsrsState, grade: Grade, groupId: Long?) -> Unit,
    onSaveLearningState: (CardLearningState) -> Unit,
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
    val form = rememberCardEditorState(cardToEdit, initialGroupId)
    if (cardToEdit != null) {
        LaunchedEffect(cardGroups) { form.loadGroups(cardGroups.map { it.id }.toSet()) }
        LaunchedEffect(cardConcepts, cardConceptWeights) {
            form.loadConcepts(cardConcepts.map { it.id }.toSet(), cardConceptWeights)
        }
    }

    var showGroupSheet by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showConceptSheet by remember { mutableStateOf(false) }
    var showCreateConcept by remember { mutableStateOf(false) }
    var weighing by remember { mutableStateOf<Concept?>(null) }
    var removingConcept by remember { mutableStateOf<Concept?>(null) }
    var resetting by remember { mutableStateOf<EditorContext?>(null) }
    val markdownToolbarState = rememberMarkdownToolbarState()
    val imagePicker = LocalImagePicker.current
    var lastLargeImageBytes by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Where the card is learnt: Global, and each learning group one of its
    // selected groups is in, through that group.
    val contexts = listOf(EditorContext.GLOBAL) + learningGroups.mapNotNull { learningGroup ->
        allGroups.firstOrNull { it.learningGroupId == learningGroup.id && it.id in form.groupIds }
            ?.let { EditorContext(learningGroup, it) }
    }
    val learningGroupContexts = contexts.drop(1)
    fun storedState(context: EditorContext): CardLearningState? =
        context.key?.let { key -> learningStates.find { it.learningGroupId == key } }
    fun stored(context: EditorContext): FsrsState =
        if (context.key == null) form.base?.fsrs ?: FsrsState() else storedState(context)?.fsrs ?: FsrsState()
    fun progressOf(context: EditorContext): ProgressEdit = form.progress[context.key] ?: ProgressEdit(stored(context))

    val requestLeave = rememberLeaveGuard(form.isDirty) { form.isDirty = false; onNavigateBack() }

    fun save() {
        if (form.question.isBlank()) return
        val card = form.editedCard()
        if (card == null) {
            onAddCard(form.question, form.answer.text, form.groupIds.toList(), form.imagePaths, form.concepts()) {
                form.isDirty = false
                onNavigateBack()
            }
            return
        }
        onUpdateCard(card, form.groupIdsToSave(), form.conceptsToSave()) {
            contexts.forEach { context ->
                val edit = form.progress[context.key] ?: return@forEach
                val key = context.key
                if (key != null) {
                    onSaveLearningState((storedState(context) ?: CardLearningState(card.id, key)).withFsrs(edit.result()))
                }
                edit.stagedGrade?.let { grade ->
                    onRecordGradeFromEdit(card.id, stored(context), edit.result(), grade, context.group?.id)
                }
            }
            form.isDirty = false
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (cardToEdit != null) "Edit Card" else "Add New Card") },
                navigationIcon = {
                    IconButton(onClick = requestLeave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = ::save, enabled = form.question.isNotBlank()) {
                        Text(if (cardToEdit != null) "Save" else "Add")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = form.question,
                    onValueChange = { form.question = it },
                    label = { Text("Concept") },
                    placeholder = { Text("e.g., En garde position") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.QuestionMark, contentDescription = null) },
                    minLines = 2,
                    maxLines = 4
                )

                MarkdownDescriptionField(
                    value = form.answer,
                    onValueChange = { form.answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    toolbarState = markdownToolbarState
                )

                EditorSection(
                    icon = Icons.Default.Image,
                    title = "Images",
                    trailing = {
                        OutlinedButton(
                            onClick = {
                                imagePicker.pick { picked ->
                                    form.imagePaths = form.imagePaths + picked.key
                                    lastLargeImageBytes = picked.byteCount.takeIf { it > LARGE_IMAGE_BYTES }
                                }
                            },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Image")
                        }
                    }
                ) {
                    if (form.imagePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CardImagesEdit(
                            imagePaths = form.imagePaths,
                            onRemoveImage = { path -> form.imagePaths = form.imagePaths.filter { it != path } },
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

                EditorSection(
                    icon = AppIcons.CardGroups,
                    title = "Groups",
                    onClick = { showGroupSheet = true },
                    trailing = { EditIcon("Edit groups") }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (form.groupIds.isEmpty()) {
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
                            allGroups.filter { it.id in form.groupIds }.forEach { group ->
                                AssistChip(
                                    onClick = { form.groupIds = form.groupIds - group.id },
                                    label = { Text(group.name) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }

                EditorSection(
                    icon = Icons.Default.Lightbulb,
                    title = "Concepts",
                    onClick = { showConceptSheet = true },
                    trailing = { EditIcon("Edit concepts") }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (form.conceptIds.isEmpty()) {
                        Text(
                            "No concepts (tap to add). Without concepts the card is always scheduled on its own.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val schedulingOffNote = schedulingOffNoteFor(form.groupIds)
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
                            allConcepts.filter { it.id in form.conceptIds }.forEach { concept ->
                                ConceptWeightChip(
                                    label = conceptChipLabel(concept.name, form.weightOf(concept.id)),
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

                // Progress, in each context the card is learnt in.
                form.base?.let { card ->
                    EditorSection(
                        icon = Icons.Default.Schedule,
                        title = "Review Schedule",
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ),
                        iconTint = MaterialTheme.colorScheme.tertiary
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        contexts.forEach { context ->
                            Spacer(modifier = Modifier.height(4.dp))
                            ReviewStatusRow(context.name, progressOf(context).result())
                        }
                    }

                    ExpandableSection(
                        icon = Icons.Default.Star,
                        title = "Quick Grade",
                        summary = "Grade this card without a practice session"
                    ) {
                        contexts.forEachIndexed { index, context ->
                            if (index > 0) Spacer(modifier = Modifier.height(16.dp))
                            ContextHeading(context)
                            Spacer(modifier = Modifier.height(8.dp))
                            GradeButtonRow { grade ->
                                onComputeGrade(card.id, grade, context.group?.id) { graded ->
                                    form.stageGrade(context.key, graded.fsrs, grade)
                                    scope.launch {
                                        val prefix = context.learningGroup?.let { "${it.name}: " } ?: ""
                                        snackbarHostState.showSnackbar(
                                            "${prefix}Grade staged — save to apply (next: ${formatDateWithoutYear(graded.nextReview)})"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    contexts.forEach { context ->
                        val edit = progressOf(context)
                        key(context.key) {
                            ExpandableSection(
                                icon = if (context.key == null) Icons.Default.Settings else AppIcons.LearningGroup,
                                title = "${context.name} Learning State",
                                summary = progressSummary(edit.result()),
                                colors = if (context.key == null) CardDefaults.cardColors() else CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                ProgressFields(
                                    edit = edit,
                                    onChange = { form.editProgress(context.key, it) },
                                    onReset = { resetting = context }
                                )
                            }
                        }
                    }
                }
            }
            // Markdown toolbar pinned above the keyboard
            MarkdownKeyboardToolbar(markdownToolbarState)
        }
    }

    if (showGroupSheet) {
        GroupPickerSheet(
            allGroups = allGroups,
            selected = form.groupIds,
            onToggle = { id -> form.groupIds = form.groupIds.toggle(id) },
            onCreate = { showCreateGroup = true },
            onDismiss = { showGroupSheet = false }
        )
    }
    if (showCreateGroup) {
        NameDialog(
            icon = AppIcons.CardGroups,
            title = "Create New Group",
            label = "Group Name",
            onCreate = { name ->
                onCreateGroup(name) { newId ->
                    form.groupIds = form.groupIds + newId
                    showCreateGroup = false
                }
            },
            onDismiss = { showCreateGroup = false }
        )
    }

    if (showConceptSheet) {
        ConceptPickerSheet(
            allConcepts = allConcepts,
            selected = form.conceptIds,
            offered = offeredConceptIdsFor(form.groupIds),
            schedulingOffNote = schedulingOffNoteFor(form.groupIds),
            onToggle = { id -> form.conceptIds = form.conceptIds.toggle(id) },
            onCreate = { showCreateConcept = true },
            onDismiss = { showConceptSheet = false }
        )
    }
    if (showCreateConcept) {
        NameDialog(
            icon = Icons.Default.Lightbulb,
            title = "Create New Concept",
            label = "Concept Name",
            supportingText = "Made available in this card's learning groups. An existing concept of the same name is reused.",
            onCreate = { name ->
                onCreateConcept(name, cardContextsFor(form.groupIds)) { newId ->
                    form.conceptIds = form.conceptIds + newId
                    showCreateConcept = false
                }
            },
            onDismiss = { showCreateConcept = false }
        )
    }
    weighing?.let { concept ->
        ConceptWeightDialog(
            concept = concept,
            weight = form.weightOf(concept.id),
            others = form.conceptIds.filter { it != concept.id }.sumOf(form::weightOf),
            onSet = { weight -> form.setWeight(concept.id, weight); weighing = null },
            onDismiss = { weighing = null }
        )
    }
    removingConcept?.let { concept ->
        RemoveConceptDialog(
            concept = concept,
            onRemove = { form.conceptIds = form.conceptIds - concept.id; removingConcept = null },
            onDismiss = { removingConcept = null }
        )
    }

    val card = form.base
    resetting?.let { context ->
        if (card == null) return@let
        val learningGroup = context.learningGroup
        ResetProgressDialog(
            title = if (learningGroup == null) "Reset Learning State?" else "Reset ${learningGroup.name} State?",
            text = when {
                learningGroup != null -> "This resets this card's progress in ${learningGroup.name} only."
                learningGroupContexts.isEmpty() -> "This will reset all learning progress for this card. The " +
                    "card will be treated as new and all FSRS data will be cleared."
                else -> "This card is learnt in ${learningGroupContexts.size} learning group(s) as well as " +
                    "Global. Choose which state(s) to reset:"
            },
            learningGroupCount = if (learningGroup == null) learningGroupContexts.size else 0,
            onReset = { global, inLearningGroups ->
                resetting = null
                when {
                    // A learning group's own reset.
                    learningGroup != null && context.group != null ->
                        onResetCardStateInGroup(card.id, context.group.id) { form.resetDone(context.key) }
                    global -> onResetCardState(card.id, inLearningGroups) { reset ->
                        form.resetDone(null, reset)
                        if (inLearningGroups) learningGroupContexts.forEach { form.resetDone(it.key) }
                    }
                    else -> learningGroupContexts.forEach { lgContext ->
                        lgContext.group?.let { group ->
                            onResetCardStateInGroup(card.id, group.id) { form.resetDone(lgContext.key) }
                        }
                    }
                }
            },
            onDismiss = { resetting = null }
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun EditIcon(description: String) {
    Icon(Icons.Default.Edit, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Global State", or a learning group's name with its icon, over that context's grade buttons. */
@Composable
private fun ContextHeading(context: EditorContext) {
    val learningGroup = context.learningGroup
    if (learningGroup == null) {
        Text("Global State", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        return
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
}
