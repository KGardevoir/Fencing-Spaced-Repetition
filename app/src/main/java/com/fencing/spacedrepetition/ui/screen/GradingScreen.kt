package com.fencing.spacedrepetition.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.ui.components.CardImagesDisplay
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.ui.components.MarkdownToolbar
import com.fencing.spacedrepetition.ui.components.applyInlineFormat
import com.fencing.spacedrepetition.ui.components.applyLinePrefix
import com.fencing.spacedrepetition.ui.viewmodel.PracticeUiState
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.util.saveImageToInternalStorage

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

    // When completed, invoke callback immediately
    LaunchedEffect(uiState) {
        if (uiState is PracticeUiState.Completed) {
            onComplete()
        }
    }

    if (uiState !is PracticeUiState.Completed) {
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
                                        },
                                        onNotesChanged = { notes, images ->
                                            viewModel.updateNotes(index, notes, images)
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

@Composable
fun GradingCardItem(
    sessionCard: SessionCard,
    cardNumber: Int,
    onGradeSelected: (Grade) -> Unit,
    onNotesChanged: (String, List<String>) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var notesExpanded by remember { mutableStateOf(false) }
    var notesValue by remember(sessionCard.card.id) {
        mutableStateOf(TextFieldValue(sessionCard.notes))
    }
    var noteImages by remember(sessionCard.card.id) {
        mutableStateOf(sessionCard.noteImagePaths)
    }
    var notesFocused by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = saveImageToInternalStorage(context, it, "review_images")
            if (savedPath != null) {
                noteImages = noteImages + savedPath
                onNotesChanged(notesValue.text, noteImages)
            }
        }
    }

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

            // Notes section
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                IconButton(onClick = { notesExpanded = !notesExpanded }) {
                    Icon(
                        imageVector = if (notesExpanded) Icons.Default.ExpandLess
                            else if (sessionCard.notes.isNotBlank() || sessionCard.noteImagePaths.isNotEmpty()) Icons.Default.EditNote
                            else Icons.Default.NoteAdd,
                        contentDescription = if (notesExpanded) "Collapse notes" else "Add notes"
                    )
                }
            }

            // Show note preview when collapsed
            if (!notesExpanded && (sessionCard.notes.isNotBlank() || sessionCard.noteImagePaths.isNotEmpty())) {
                if (sessionCard.notes.isNotBlank()) {
                    MarkdownText(
                        text = sessionCard.notes,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (sessionCard.noteImagePaths.isNotEmpty()) {
                    CardImagesDisplay(
                        imagePaths = sessionCard.noteImagePaths,
                        modifier = Modifier.fillMaxWidth(),
                        maxHeight = 80
                    )
                }
            }

            // Expanded notes editor
            if (notesExpanded) {
                MarkdownDescriptionField(
                    value = notesValue,
                    onValueChange = {
                        notesValue = it
                        onNotesChanged(it.text, noteImages)
                    },
                    label = "Notes",
                    minLines = 2,
                    maxLines = 5,
                    onFocusChanged = { notesFocused = it }
                )

                // Markdown formatting toolbar (slides in when focused)
                AnimatedVisibility(
                    visible = notesFocused,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider()
                        MarkdownToolbar(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            onBold      = { notesValue = applyInlineFormat(notesValue, "**",  "**",   "bold"); onNotesChanged(notesValue.text, noteImages) },
                            onItalic    = { notesValue = applyInlineFormat(notesValue, "*",   "*",    "italic"); onNotesChanged(notesValue.text, noteImages) },
                            onUnderline = { notesValue = applyInlineFormat(notesValue, "<u>", "</u>", "underline"); onNotesChanged(notesValue.text, noteImages) },
                            onCode      = { notesValue = applyInlineFormat(notesValue, "`",   "`",    "code"); onNotesChanged(notesValue.text, noteImages) },
                            onHeader    = { notesValue = applyLinePrefix(notesValue, "# "); onNotesChanged(notesValue.text, noteImages) },
                            onBullet    = { notesValue = applyLinePrefix(notesValue, "- "); onNotesChanged(notesValue.text, noteImages) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (noteImages.isNotEmpty()) {
                    CardImagesEdit(
                        imagePaths = noteImages,
                        onRemoveImage = { path ->
                            noteImages = noteImages - path
                            onNotesChanged(notesValue.text, noteImages)
                        },
                        maxHeight = 100
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Image")
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
