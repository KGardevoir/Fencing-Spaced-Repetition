package com.fencing.spacedrepetition.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Grade
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCardScreen(
    viewModel: CardViewModel,
    groupViewModel: GroupViewModel,
    cardToEdit: Card? = null,
    initialGroupId: Long? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var question by remember { mutableStateOf(cardToEdit?.question ?: "") }
    var answer by remember { mutableStateOf(cardToEdit?.answer ?: "") }
    var selectedAlgorithm by remember { mutableStateOf(cardToEdit?.algorithm ?: AlgorithmType.FSRS) }
    var imagePaths by remember { mutableStateOf<List<String>>(cardToEdit?.imagePaths?.toMutableList() ?: mutableListOf()) }

    // Group selection state
    val allGroups by groupViewModel.allGroups.collectAsState()
    val cardGroups = cardToEdit?.let { card ->
        viewModel.getGroupsForCard(card.id).collectAsState(initial = emptyList()).value
    } ?: emptyList()

    var selectedGroupIds by remember(cardGroups, initialGroupId) {
        val initialIds = cardGroups.map { it.id }.toMutableSet()
        // For new cards, pre-select the initial group if provided
        if (cardToEdit == null && initialGroupId != null) {
            initialIds.add(initialGroupId)
        }
        mutableStateOf(initialIds.toSet())
    }

    var showGroupSelectionSheet by remember { mutableStateOf(false) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var isDirty by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copy image to app's internal storage and save path
            val savedPath = saveImageToInternalStorage(context, it)
            if (savedPath != null) {
                imagePaths = (imagePaths + savedPath).toMutableList()
                isDirty = true
            }
        }
    }

    // Independent learning states
    val learningStates by cardToEdit?.let { card ->
        viewModel.getLearningStatesForCard(card.id).collectAsState(initial = emptyList())
    } ?: remember { mutableStateOf(emptyList<CardGroupLearningState>()) }

    // Mutable state for editing independent learning states (groupId -> state fields)
    var independentLearningEdits by remember(learningStates) {
        mutableStateOf(
            learningStates.associate { state ->
                state.groupId to IndependentLearningEdit(
                    fsrsStability = state.fsrsStability.toString(),
                    fsrsDifficulty = state.fsrsDifficulty.toString(),
                    fsrsReps = state.fsrsReps.toString(),
                    fsrsLapses = state.fsrsLapses.toString(),
                    fsrsState = state.fsrsState,
                    sm2EaseFactor = state.sm2EaseFactor.toString(),
                    sm2Interval = state.sm2Interval.toString(),
                    sm2Repetitions = state.sm2Repetitions.toString()
                )
            }
        )
    }

    // Learning state fields (only for editing)
    var fsrsStability by remember { mutableStateOf(cardToEdit?.fsrsStability?.toString() ?: "0.0") }
    var fsrsDifficulty by remember { mutableStateOf(cardToEdit?.fsrsDifficulty?.toString() ?: "0.0") }
    var fsrsReps by remember { mutableStateOf(cardToEdit?.fsrsReps?.toString() ?: "0") }
    var fsrsLapses by remember { mutableStateOf(cardToEdit?.fsrsLapses?.toString() ?: "0") }
    var fsrsState by remember { mutableStateOf(cardToEdit?.fsrsState ?: "NEW") }
    var sm2EaseFactor by remember { mutableStateOf(cardToEdit?.sm2EaseFactor?.toString() ?: "2.5") }
    var sm2Interval by remember { mutableStateOf(cardToEdit?.sm2Interval?.toString() ?: "0") }
    var sm2Repetitions by remember { mutableStateOf(cardToEdit?.sm2Repetitions?.toString() ?: "0") }

    // Additional state for review timing (updated by quick grading)
    var lastReview by remember { mutableStateOf(cardToEdit?.lastReview ?: 0L) }
    var nextReview by remember { mutableStateOf(cardToEdit?.nextReview ?: 0L) }
    var fsrsElapsedDays by remember { mutableStateOf(cardToEdit?.fsrsElapsedDays?.toString() ?: "0") }
    var fsrsScheduledDays by remember { mutableStateOf(cardToEdit?.fsrsScheduledDays?.toString() ?: "0") }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Question input
            OutlinedTextField(
                value = question,
                onValueChange = { question = it; isDirty = true },
                label = { Text("Question / Concept") },
                placeholder = { Text("e.g., En garde position") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.QuestionMark, contentDescription = null)
                },
                minLines = 2,
                maxLines = 4
            )

            // Answer input
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it; isDirty = true },
                label = { Text("Description") },
                placeholder = { Text("e.g., Front foot pointed forward, back foot at 90°...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                },
                minLines = 3,
                maxLines = 8
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
                            onClick = { imagePickerLauncher.launch("image/*") },
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
                                    nextReview <= System.currentTimeMillis() -> MaterialTheme.colorScheme.error
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
                                        groupReview <= System.currentTimeMillis() -> MaterialTheme.colorScheme.error
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
                                            viewModel.gradeCard(cardToEdit.id, grade) { updated ->
                                                fsrsStability = updated.fsrsStability.toString()
                                                fsrsDifficulty = updated.fsrsDifficulty.toString()
                                                fsrsReps = updated.fsrsReps.toString()
                                                fsrsLapses = updated.fsrsLapses.toString()
                                                fsrsState = updated.fsrsState
                                                fsrsElapsedDays = updated.fsrsElapsedDays.toString()
                                                fsrsScheduledDays = updated.fsrsScheduledDays.toString()
                                                sm2EaseFactor = updated.sm2EaseFactor.toString()
                                                sm2Interval = updated.sm2Interval.toString()
                                                sm2Repetitions = updated.sm2Repetitions.toString()
                                                lastReview = updated.lastReview
                                                nextReview = updated.nextReview
                                                scope.launch {
                                                    val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault())
                                                        .format(Date(updated.nextReview))
                                                    snackbarHostState.showSnackbar(
                                                        "Graded as ${grade.label} - Next review: $dateStr"
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
                                                    viewModel.gradeCard(cardToEdit.id, grade, group.id) { updated ->
                                                        independentLearningEdits = independentLearningEdits + (group.id to IndependentLearningEdit(
                                                            fsrsStability = updated.fsrsStability.toString(),
                                                            fsrsDifficulty = updated.fsrsDifficulty.toString(),
                                                            fsrsReps = updated.fsrsReps.toString(),
                                                            fsrsLapses = updated.fsrsLapses.toString(),
                                                            fsrsState = updated.fsrsState,
                                                            sm2EaseFactor = updated.sm2EaseFactor.toString(),
                                                            sm2Interval = updated.sm2Interval.toString(),
                                                            sm2Repetitions = updated.sm2Repetitions.toString()
                                                        ))
                                                        groupNextReviews = groupNextReviews + (group.id to updated.nextReview)
                                                        scope.launch {
                                                            val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault())
                                                                .format(Date(updated.nextReview))
                                                            snackbarHostState.showSnackbar(
                                                                "${group.name}: Graded as ${grade.label} - Next: $dateStr"
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

            // Algorithm selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Spaced Repetition Algorithm",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // FSRS option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAlgorithm == AlgorithmType.FSRS,
                            onClick = { selectedAlgorithm = AlgorithmType.FSRS; isDirty = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FSRS (Recommended)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Modern algorithm with better predictions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SM-2 option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAlgorithm == AlgorithmType.SM2,
                            onClick = { selectedAlgorithm = AlgorithmType.SM2; isDirty = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SM-2 (Classic)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Traditional SuperMemo algorithm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                            nextReview <= System.currentTimeMillis() -> "Due for review"
                            else -> {
                                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                "Next review: ${formatter.format(Date(nextReview))}"
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
                            if (selectedAlgorithm == AlgorithmType.FSRS) {
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

                            // SM-2 Parameters
                            if (selectedAlgorithm == AlgorithmType.SM2) {
                                Text(
                                    "SM-2 Parameters",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = sm2EaseFactor,
                                    onValueChange = { sm2EaseFactor = it; isDirty = true },
                                    label = { Text("Ease Factor") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = { Text("Default: 2.5, Range: 1.3 - 4.0") }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = sm2Interval,
                                        onValueChange = { sm2Interval = it; isDirty = true },
                                        label = { Text("Interval (days)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = sm2Repetitions,
                                        onValueChange = { sm2Repetitions = it; isDirty = true },
                                        label = { Text("Repetitions") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
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
                            selectedAlgorithm = selectedAlgorithm,
                            onStateChange = { newEdit ->
                                independentLearningEdits = independentLearningEdits + (group.id to newEdit)
                                isDirty = true
                            },
                            onReset = {
                                cardToEdit?.let { card ->
                                    viewModel.resetCardStateInGroup(card.id, group.id) {
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
                                answer = answer,
                                algorithm = selectedAlgorithm,
                                imagePaths = imagePaths,
                                // FSRS state
                                fsrsStability = fsrsStability.toDoubleOrNull() ?: cardToEdit.fsrsStability,
                                fsrsDifficulty = fsrsDifficulty.toDoubleOrNull() ?: cardToEdit.fsrsDifficulty,
                                fsrsReps = fsrsReps.toIntOrNull() ?: cardToEdit.fsrsReps,
                                fsrsLapses = fsrsLapses.toIntOrNull() ?: cardToEdit.fsrsLapses,
                                fsrsState = fsrsState,
                                fsrsElapsedDays = fsrsElapsedDays.toIntOrNull() ?: cardToEdit.fsrsElapsedDays,
                                fsrsScheduledDays = fsrsScheduledDays.toIntOrNull() ?: cardToEdit.fsrsScheduledDays,
                                // SM2 state
                                sm2EaseFactor = sm2EaseFactor.toDoubleOrNull() ?: cardToEdit.sm2EaseFactor,
                                sm2Interval = sm2Interval.toIntOrNull() ?: cardToEdit.sm2Interval,
                                sm2Repetitions = sm2Repetitions.toIntOrNull() ?: cardToEdit.sm2Repetitions,
                                // Review timing
                                lastReview = lastReview,
                                nextReview = nextReview
                            )
                            viewModel.updateCard(
                                updatedCard,
                                groupIds = selectedGroupIds.toList(),
                                onSuccess = {
                                    // Save independent learning state changes
                                    independentLearningEdits.forEach { (groupId, edit) ->
                                        val existingState = learningStates.find { it.groupId == groupId }
                                        if (existingState != null) {
                                            val updatedState = existingState.copy(
                                                fsrsStability = edit.fsrsStability.toDoubleOrNull() ?: existingState.fsrsStability,
                                                fsrsDifficulty = edit.fsrsDifficulty.toDoubleOrNull() ?: existingState.fsrsDifficulty,
                                                fsrsReps = edit.fsrsReps.toIntOrNull() ?: existingState.fsrsReps,
                                                fsrsLapses = edit.fsrsLapses.toIntOrNull() ?: existingState.fsrsLapses,
                                                fsrsState = edit.fsrsState,
                                                sm2EaseFactor = edit.sm2EaseFactor.toDoubleOrNull() ?: existingState.sm2EaseFactor,
                                                sm2Interval = edit.sm2Interval.toIntOrNull() ?: existingState.sm2Interval,
                                                sm2Repetitions = edit.sm2Repetitions.toIntOrNull() ?: existingState.sm2Repetitions
                                            )
                                            viewModel.updateLearningState(updatedState)
                                        }
                                    }
                                    isDirty = false
                                    onNavigateBack()
                                }
                            )
                        } else {
                            viewModel.addCard(
                                question = question,
                                answer = answer,
                                groupIds = selectedGroupIds.toList(),
                                algorithm = selectedAlgorithm,
                                imagePaths = imagePaths,
                                onSuccess = { isDirty = false; onNavigateBack() }
                            )
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
        }
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
                    allGroups.forEach { group ->
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
                                viewModel.resetCardState(cardToEdit.id, resetGroupStates = false) {
                                    // Reset local global state variables
                                    fsrsStability = "0.0"
                                    fsrsDifficulty = "0.0"
                                    fsrsReps = "0"
                                    fsrsLapses = "0"
                                    fsrsState = "NEW"
                                    sm2EaseFactor = "2.5"
                                    sm2Interval = "0"
                                    sm2Repetitions = "0"
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
                                    viewModel.resetCardStateInGroup(cardToEdit.id, group.id) {
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
                                viewModel.resetCardState(cardToEdit.id, resetGroupStates = true) {
                                    // Reset local global state
                                    fsrsStability = "0.0"
                                    fsrsDifficulty = "0.0"
                                    fsrsReps = "0"
                                    fsrsLapses = "0"
                                    fsrsState = "NEW"
                                    sm2EaseFactor = "2.5"
                                    sm2Interval = "0"
                                    sm2Repetitions = "0"
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
                text = { Text("This will reset all learning progress for this card. The card will be treated as new and all FSRS/SM-2 data will be cleared.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetCardState(cardToEdit.id) {
                                // Reset local state variables too
                                fsrsStability = "0.0"
                                fsrsDifficulty = "0.0"
                                fsrsReps = "0"
                                fsrsLapses = "0"
                                fsrsState = "NEW"
                                sm2EaseFactor = "2.5"
                                sm2Interval = "0"
                                sm2Repetitions = "0"
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
        var newGroupName by remember { mutableStateOf("") }
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
                            groupViewModel.addGroup(newGroupName) { newId ->
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
    selectedAlgorithm: AlgorithmType,
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
        fsrsState = groupState?.fsrsState ?: "NEW",
        sm2EaseFactor = groupState?.sm2EaseFactor?.toString() ?: "2.5",
        sm2Interval = groupState?.sm2Interval?.toString() ?: "0",
        sm2Repetitions = groupState?.sm2Repetitions?.toString() ?: "0"
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
            } else if (groupState.nextReview <= System.currentTimeMillis()) {
                "Due for review"
            } else {
                val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                "Next: ${formatter.format(Date(groupState.nextReview))}"
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
                if (selectedAlgorithm == AlgorithmType.FSRS) {
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

                // SM-2 Parameters
                if (selectedAlgorithm == AlgorithmType.SM2) {
                    Text(
                        "SM-2 Parameters",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = currentEdit.sm2EaseFactor,
                        onValueChange = { onStateChange(currentEdit.copy(sm2EaseFactor = it)) },
                        label = { Text("Ease Factor") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = currentEdit.sm2Interval,
                            onValueChange = { onStateChange(currentEdit.copy(sm2Interval = it)) },
                            label = { Text("Interval") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = currentEdit.sm2Repetitions,
                            onValueChange = { onStateChange(currentEdit.copy(sm2Repetitions = it)) },
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
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
    val fsrsState: String = "NEW",
    val sm2EaseFactor: String = "2.5",
    val sm2Interval: String = "0",
    val sm2Repetitions: String = "0"
)

/** Formats the next-review status for display. */
private fun formatReviewStatus(fsrsState: String, nextReview: Long): String = when {
    fsrsState == "NEW" && nextReview == 0L -> "New card"
    nextReview <= System.currentTimeMillis() -> "Due now"
    else -> {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        formatter.format(Date(nextReview))
    }
}

/**
 * Save image from URI to app's internal storage
 * Returns the saved file path or null if failed
 */
private fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // Create images directory if it doesn't exist
        val imagesDir = File(context.filesDir, "card_images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // Generate unique filename
        val timestamp = System.currentTimeMillis()
        val extension = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "jpg"
        val fileName = "card_image_${timestamp}.${extension}"
        val outputFile = File(imagesDir, fileName)

        // Copy file
        FileOutputStream(outputFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()

        // Return the file URI as string
        outputFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
