package com.fencing.spacedrepetition.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.GROUP_NAME_CARD_EDIT
import com.fencing.spacedrepetition.ui.components.CardImagesDisplay
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownKeyboardToolbar
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.ui.components.MarkdownToolbarState
import com.fencing.spacedrepetition.ui.components.rememberMarkdownToolbarState
import com.fencing.spacedrepetition.ui.viewmodel.HistoryItem
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.ReviewLogWithCard
import com.fencing.spacedrepetition.util.saveImageToInternalStorage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val historyItems by viewModel.historyItems.collectAsState()
    val markdownToolbarState = rememberMarkdownToolbarState()

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            if (historyItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(historyItems, key = { item ->
                        when (item) {
                            is HistoryItem.Session -> "session-${item.session.id}"
                            is HistoryItem.QuickGrade -> "quickgrade-${item.log.reviewLog.id}"
                        }
                    }) { item ->
                        when (item) {
                            is HistoryItem.Session -> SessionHistoryCard(
                                session = item.session,
                                viewModel = viewModel,
                                toolbarState = markdownToolbarState
                            )
                            is HistoryItem.QuickGrade -> QuickGradeCard(
                                logWithCard = item.log,
                                viewModel = viewModel,
                                toolbarState = markdownToolbarState
                            )
                        }
                    }
                }

                // Markdown toolbar pinned above the keyboard
                MarkdownKeyboardToolbar(markdownToolbarState)
            }
        }
    }
}

@Composable
fun SessionHistoryCard(
    session: PracticeSession,
    viewModel: HistoryViewModel,
    toolbarState: MarkdownToolbarState? = null
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
                            ReviewLogRow(
                                logWithCard = logWithCard,
                                viewModel = viewModel,
                                toolbarState = toolbarState
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A single non-session grade (from the Add/Edit card screen), shown inline in the list. */
@Composable
private fun QuickGradeCard(
    logWithCard: ReviewLogWithCard,
    viewModel: HistoryViewModel,
    toolbarState: MarkdownToolbarState? = null
) {
    val log = logWithCard.reviewLog
    val grade = Grade.fromValue(log.grade)
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }
    var showNoteEditor by remember { mutableStateOf(false) }

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

    val noteImages = remember(log.imagePaths) {
        log.imagePaths.split(",").filter { it.isNotBlank() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = MaterialTheme.shapes.small, color = gradeColor) {
                    Text(
                        text = gradeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = gradeContentColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = logWithCard.cardQuestion,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val groupLabel = log.groupName?.takeIf { it != GROUP_NAME_CARD_EDIT }
                    Text(
                        text = buildString {
                            append("Quick Grade")
                            if (groupLabel != null) append(" · $groupLabel")
                            append(" · ")
                            append(dateFormatter.format(Date(log.reviewTime)))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "+${log.scheduledDays}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = { showNoteEditor = !showNoteEditor },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (log.notes.isNotBlank() || noteImages.isNotEmpty())
                            Icons.Default.EditNote else Icons.Default.NoteAdd,
                        contentDescription = "Edit notes",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Display existing notes/images inline when not editing
            if (!showNoteEditor && (log.notes.isNotBlank() || noteImages.isNotEmpty())) {
                Spacer(modifier = Modifier.height(8.dp))
                if (log.notes.isNotBlank()) {
                    MarkdownText(text = log.notes)
                }
                if (noteImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CardImagesDisplay(imagePaths = noteImages, maxHeight = 80)
                }
            }

            // Note editor
            AnimatedVisibility(visible = showNoteEditor) {
                HistoryNoteEditor(
                    reviewLog = log,
                    onSave = { notes, images ->
                        viewModel.updateReviewLogNotes(log, notes, images)
                        showNoteEditor = false
                    },
                    toolbarState = toolbarState
                )
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
private fun ReviewLogRow(
    logWithCard: ReviewLogWithCard,
    viewModel: HistoryViewModel,
    toolbarState: MarkdownToolbarState? = null
) {
    val log = logWithCard.reviewLog
    val grade = Grade.fromValue(log.grade)
    var showNoteEditor by remember { mutableStateOf(false) }

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

    val noteImages = remember(log.imagePaths) {
        log.imagePaths.split(",").filter { it.isNotBlank() }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = logWithCard.cardQuestion,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val groupLabel = log.groupName?.takeIf { it != GROUP_NAME_CARD_EDIT }
                if (groupLabel != null) {
                    Text(
                        text = groupLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "+${log.scheduledDays}d",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = { showNoteEditor = !showNoteEditor },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (log.notes.isNotBlank() || noteImages.isNotEmpty())
                        Icons.Default.EditNote else Icons.Default.NoteAdd,
                    contentDescription = "Edit notes",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Display existing notes/images inline when not editing
        if (!showNoteEditor && (log.notes.isNotBlank() || noteImages.isNotEmpty())) {
            Spacer(modifier = Modifier.height(4.dp))
            if (log.notes.isNotBlank()) {
                MarkdownText(
                    text = log.notes,
                    modifier = Modifier.padding(start = 60.dp)
                )
            }
            if (noteImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                CardImagesDisplay(
                    imagePaths = noteImages,
                    modifier = Modifier.padding(start = 60.dp),
                    maxHeight = 80
                )
            }
        }

        // Note editor
        AnimatedVisibility(visible = showNoteEditor) {
            HistoryNoteEditor(
                reviewLog = log,
                onSave = { notes, images ->
                    viewModel.updateReviewLogNotes(log, notes, images)
                    showNoteEditor = false
                },
                toolbarState = toolbarState
            )
        }
    }
}

/**
 * Inline note editor for a review log entry. Supports markdown notes and image attachments.
 */
@Composable
private fun HistoryNoteEditor(
    reviewLog: ReviewLog,
    onSave: (String, List<String>) -> Unit,
    toolbarState: MarkdownToolbarState? = null
) {
    val context = LocalContext.current
    var notesValue by remember(reviewLog.id) {
        mutableStateOf(TextFieldValue(reviewLog.notes))
    }
    var noteImages by remember(reviewLog.id) {
        mutableStateOf(
            reviewLog.imagePaths.split(",").filter { it.isNotBlank() }
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = saveImageToInternalStorage(context, it, "review_images")
            if (savedPath != null) {
                noteImages = noteImages + savedPath
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        MarkdownDescriptionField(
            value = notesValue,
            onValueChange = { notesValue = it },
            label = "Notes",
            minLines = 2,
            maxLines = 5,
            toolbarState = toolbarState
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (noteImages.isNotEmpty()) {
            CardImagesEdit(
                imagePaths = noteImages,
                onRemoveImage = { path ->
                    noteImages = noteImages - path
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

            Button(
                onClick = { onSave(notesValue.text, noteImages) }
            ) {
                Text("Save")
            }
        }
    }
}
