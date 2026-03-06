package com.fencing.spacedrepetition.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.ui.components.CardImagesDisplay
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.ui.viewmodel.PracticeUiState
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.util.saveImageToInternalStorage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradingScreen(
    viewModel: PracticeViewModel,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionCards by viewModel.sessionCards.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }

    when (val state = uiState) {
        is PracticeUiState.AddingNotes -> {
            PostGradingNotesScreen(
                viewModel = viewModel,
                practiceStartTime = state.practiceStartTime,
                sessionCards = sessionCards,
                onDone = onComplete
            )
        }
        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Grade Your Performance") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (uiState) {
                        is PracticeUiState.Submitting -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        is PracticeUiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = (uiState as PracticeUiState.Error).message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                // Instructions
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Rate each card based on how well you recalled it during practice. Skip cards that couldn't be trained.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Cards list
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(sessionCards) { index, sessionCard ->
                                        GradingCardItem(
                                            sessionCard = sessionCard,
                                            cardNumber = index + 1,
                                            onGradeSelected = { grade ->
                                                viewModel.updateGrade(index, grade)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Submit button
                                Button(
                                    onClick = { showConfirmDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = sessionCards.all { it.grade != null }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Submit Grades",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                // Show warning if not all cards graded
                                if (sessionCards.any { it.grade == null }) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Please grade all cards before submitting",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Confirmation dialog
            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    title = { Text("Submit Grades?") },
                    text = {
                        Text("Are you sure you want to submit these grades? This will update your card schedules.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                viewModel.submitGrades()
                            }
                        ) {
                            Text("Submit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGradingNotesScreen(
    viewModel: PracticeViewModel,
    practiceStartTime: Long,
    sessionCards: List<SessionCard>,
    onDone: () -> Unit
) {
    val reviewLogs by viewModel.sessionReviewLogs.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    val startTimeText = remember(practiceStartTime) {
        if (practiceStartTime > 0) dateFormatter.format(Date(practiceStartTime)) else ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Session Notes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                actions = {
                    TextButton(onClick = onDone) {
                        Text("Done")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Session info
            if (startTimeText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Practice started",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = startTimeText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Optionally add notes or pictures to any graded card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(reviewLogs) { index, log ->
                    val card = sessionCards.find { it.card.id == log.cardId }
                    ReviewLogNoteEditor(
                        reviewLog = log,
                        cardQuestion = card?.card?.question ?: "Card ${index + 1}",
                        grade = Grade.fromValue(log.grade),
                        onSaveNotes = { notes, images ->
                            viewModel.updateReviewLogNotes(log.id, notes, images)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Finish",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun ReviewLogNoteEditor(
    reviewLog: ReviewLog,
    cardQuestion: String,
    grade: Grade?,
    onSaveNotes: (String, List<String>) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var notesValue by remember(reviewLog.id) {
        mutableStateOf(TextFieldValue(reviewLog.notes))
    }
    var noteImages by remember(reviewLog.id) {
        mutableStateOf(
            reviewLog.imagePaths.split(",").filter { it.isNotBlank() }
        )
    }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = saveImageToInternalStorage(context, it, "review_images")
            if (savedPath != null) {
                noteImages = noteImages + savedPath
                hasUnsavedChanges = true
            }
        }
    }

    val gradeColor = when (grade) {
        Grade.AGAIN -> MaterialTheme.colorScheme.errorContainer
        Grade.HARD -> MaterialTheme.colorScheme.tertiaryContainer
        Grade.GOOD -> MaterialTheme.colorScheme.secondaryContainer
        Grade.EASY -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val gradeContentColor = when (grade) {
        Grade.AGAIN -> MaterialTheme.colorScheme.onErrorContainer
        Grade.HARD -> MaterialTheme.colorScheme.onTertiaryContainer
        Grade.GOOD -> MaterialTheme.colorScheme.onSecondaryContainer
        Grade.EASY -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: card question + grade chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardQuestion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(shape = MaterialTheme.shapes.small, color = gradeColor) {
                    Text(
                        text = grade?.label ?: "?",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = gradeContentColor
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.NoteAdd,
                        contentDescription = if (expanded) "Collapse" else "Add notes"
                    )
                }
            }

            // Show existing note preview when collapsed
            if (!expanded && (reviewLog.notes.isNotBlank() || noteImages.isNotEmpty())) {
                Spacer(modifier = Modifier.height(4.dp))
                if (reviewLog.notes.isNotBlank()) {
                    MarkdownText(
                        text = reviewLog.notes,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (noteImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CardImagesDisplay(
                        imagePaths = noteImages,
                        modifier = Modifier.fillMaxWidth(),
                        maxHeight = 80
                    )
                }
            }

            // Expanded editor
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                MarkdownDescriptionField(
                    value = notesValue,
                    onValueChange = {
                        notesValue = it
                        hasUnsavedChanges = true
                    },
                    label = "Notes",
                    minLines = 2,
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Image management
                if (noteImages.isNotEmpty()) {
                    CardImagesEdit(
                        imagePaths = noteImages,
                        onRemoveImage = { path ->
                            noteImages = noteImages - path
                            hasUnsavedChanges = true
                        },
                        maxHeight = 100
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Image")
                    }

                    if (hasUnsavedChanges) {
                        Button(
                            onClick = {
                                onSaveNotes(notesValue.text, noteImages)
                                hasUnsavedChanges = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradingCardItem(
    sessionCard: SessionCard,
    cardNumber: Int,
    onGradeSelected: (Grade) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sessionCard.grade != null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Card header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Card $cardNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Show/hide button
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Hide" else "Show"
                    )
                }
            }

            // Question (always visible)
            Text(
                text = sessionCard.card.question,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Answer (expandable)
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Description:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                MarkdownText(
                    text = sessionCard.card.answer,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Display images if available
                if (sessionCard.card.imagePaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CardImagesDisplay(
                        imagePaths = sessionCard.card.imagePaths,
                        modifier = Modifier.fillMaxWidth(),
                        maxHeight = 150
                    )
                }

                if (sessionCard.card.category.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text(sessionCard.card.category, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grade selection
            Text(
                text = "How well did you recall this?",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Grade.values().forEach { grade ->
                    GradeButton(
                        grade = grade,
                        selected = sessionCard.grade == grade,
                        onClick = { onGradeSelected(grade) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun GradeButton(
    grade: Grade,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (grade) {
        Grade.SKIP -> Pair(
            MaterialTheme.colorScheme.outline,
            Icons.Default.SkipNext
        )
        Grade.AGAIN -> Pair(
            MaterialTheme.colorScheme.error,
            Icons.Default.Close
        )
        Grade.HARD -> Pair(
            Color(0xFFFF9800),
            Icons.Default.Remove
        )
        Grade.GOOD -> Pair(
            Color(0xFF4CAF50),
            Icons.Default.Check
        )
        Grade.EASY -> Pair(
            Color(0xFF2196F3),
            Icons.Default.Done
        )
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) color.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = color
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) color else MaterialTheme.colorScheme.outline
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = grade.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
