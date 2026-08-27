// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

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
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Grade
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.fencing.spacedrepetition.data.model.Group
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
    learningStates: List<CardGroupLearningState>,
    cardToEdit: Card? = null,
    initialGroupId: Long? = null,
    onComputeGrade: (cardId: Long, grade: Grade, groupId: Long?, onComputed: (Card) -> Unit) -> Unit,
    onResetCardState: (cardId: Long, resetGroupStates: Boolean, onDone: () -> Unit) -> Unit,
    onResetCardStateInGroup: (cardId: Long, groupId: Long, onDone: () -> Unit) -> Unit,
    onUpdateCard: (card: Card, groupIds: List<Long>, onSuccess: () -> Unit) -> Unit,
    onRecordGradeFromEdit: (before: Card, after: Card, grade: Grade, groupId: Long?) -> Unit,
    onUpdateLearningState: (CardGroupLearningState) -> Unit,
    onAddCard: (
        question: String,
        answer: String,
        groupIds: List<Long>,
        imagePaths: List<String>,
        onSuccess: () -> Unit
    ) -> Unit,
    onCreateGroup: (name: String, onCreated: (Long) -> Unit) -> Unit,
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

    var showGroupSelectionSheet by rememberSaveable { mutableStateOf(false) }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateGroupDialog by rememberSaveable { mutableStateOf(false) }
    var isDirty by rememberSaveable { mutableStateOf(false) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    val markdownToolbarState = rememberMarkdownToolbarState()

    val imagePicker = LocalImagePicker.current
    var lastLargeImageBytes by remember { mutableStateOf<Int?>(null) }

    // Mutable state for editing independent learning states (groupId -> state fields)
    var independentLearningEdits by remember(learningStates) {
        mutableStateOf(
            learningStates.associate { state ->
                state.groupId to IndependentLearningEdit(
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
            learningStates.associate { it.groupId to it.nextReview }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isEditing = cardToEdit != null

    BackHandler(enabled = isDirty) {
        showUnsavedChangesDialog = true
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
                                Icons.Default.Folder,
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

            // Next review schedule (only when editing)
            if (isEditing && cardToEdit != null) {
                val independentGroups = allGroups.filter {
                    it.independentLearning && it.id in selectedGroupIds
                }

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
                        independentGroups.forEach { group ->
                            val groupReview = groupNextReviews[group.id] ?: 0L
                            val groupState = learningStates.find { it.groupId == group.id }
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

                            // Independent learning group grading
                            val independentGradeGroups = allGroups.filter {
                                it.independentLearning && it.id in selectedGroupIds
                            }

                            if (independentGradeGroups.isNotEmpty()) {
                                independentGradeGroups.forEach { group ->
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "${group.name} (Independent)",
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
                                                        independentLearningEdits = independentLearningEdits + (group.id to IndependentLearningEdit(
                                                            fsrsStability = updated.fsrsStability.toString(),
                                                            fsrsDifficulty = updated.fsrsDifficulty.toString(),
                                                            fsrsReps = updated.fsrsReps.toString(),
                                                            fsrsLapses = updated.fsrsLapses.toString(),
                                                            fsrsState = updated.fsrsState
                                                        ))
                                                        groupNextReviews = groupNextReviews + (group.id to updated.nextReview)
                                                        appliedGrade = grade
                                                        appliedGradeGroupId = group.id
                                                        isDirty = true
                                                        scope.launch {
                                                            val dateStr = formatDateWithoutYear(updated.nextReview)
                                                            snackbarHostState.showSnackbar(
                                                                "${group.name}: Grade staged — save to apply (next: $dateStr)"
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

                // Independent Learning States section
                val independentGroups = allGroups.filter {
                    it.independentLearning && it.id in selectedGroupIds
                }
                if (independentGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    independentGroups.forEach { group ->
                        val groupState = learningStates.find { it.groupId == group.id }
                        val editState = independentLearningEdits[group.id]

                        IndependentLearningStateCard(
                            groupName = group.name,
                            groupState = groupState,
                            editState = editState,
                            onStateChange = { newEdit ->
                                independentLearningEdits = independentLearningEdits + (group.id to newEdit)
                                isDirty = true
                            },
                            onReset = {
                                cardToEdit?.let { card ->
                                    onResetCardStateInGroup(card.id, group.id) {
                                        // Reset local edit state
                                        independentLearningEdits = independentLearningEdits + (group.id to IndependentLearningEdit())
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Button(
                onClick = {
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
                                selectedGroupIds.toList()
                            ) {
                                // Record a history entry if a grade was applied via the grade buttons
                                val grade = appliedGrade
                                if (grade != null && cardToEdit != null) {
                                    onRecordGradeFromEdit(cardToEdit, updatedCard, grade, appliedGradeGroupId)
                                }
                                // Save independent learning state changes
                                independentLearningEdits.forEach { (groupId, edit) ->
                                    val existingState = learningStates.find { it.groupId == groupId }
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
                                imagePaths
                            ) { isDirty = false; onNavigateBack() }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = question.isNotBlank()
            ) {
                Icon(
                    if (isEditing) Icons.Default.Save else Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditing) "Save Changes" else "Add Card",
                    style = MaterialTheme.typography.titleMedium
                )
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
        val independentLearningGroups = cardGroups.filter { it.independentLearning }

        if (independentLearningGroups.isNotEmpty()) {
            // Show enhanced dialog with options
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                title = { Text("Reset Learning State") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "This card is in ${independentLearningGroups.size} group(s) with independent learning.",
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
                                independentLearningGroups.forEach { group ->
                                    onResetCardStateInGroup(cardToEdit.id, group.id) {
                                        // Reset local edit state for this group
                                        independentLearningEdits = independentLearningEdits + (group.id to IndependentLearningEdit())
                                    }
                                }
                                showResetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset Group States Only (${independentLearningGroups.size})")
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
                                independentLearningGroups.forEach { group ->
                                    independentLearningEdits = independentLearningEdits + (group.id to IndependentLearningEdit())
                                }
                                showResetDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reset Both Global & Group States")
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

    // Create new group dialog
    if (showCreateGroupDialog) {
        var newGroupName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
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
fun IndependentLearningStateCard(
    groupName: String,
    groupState: CardGroupLearningState?,
    editState: IndependentLearningEdit?,
    onStateChange: (IndependentLearningEdit) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // Use edit state if available, otherwise use group state or defaults
    val currentEdit = editState ?: IndependentLearningEdit(
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
                        Icons.Default.Folder,
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
data class IndependentLearningEdit(
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

