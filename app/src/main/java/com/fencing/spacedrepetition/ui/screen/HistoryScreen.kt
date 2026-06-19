package com.fencing.spacedrepetition.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.GROUP_NAME_CARD_EDIT
import com.fencing.spacedrepetition.ui.components.CardImagesDisplay
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownKeyboardToolbar
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.ui.components.MarkdownToolbarState
import com.fencing.spacedrepetition.ui.components.OpponentPicker
import com.fencing.spacedrepetition.ui.components.rememberMarkdownToolbarState
import com.fencing.spacedrepetition.ui.viewmodel.HistoryItem
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OPPONENT_FILTER_NONE
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
    val opponents by viewModel.opponents.collectAsState()
    val opponentFilter by viewModel.opponentFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val availableGroups by viewModel.availableGroups.collectAsState()
    val markdownToolbarState = rememberMarkdownToolbarState()
    val hasActiveFilters = searchQuery.isNotBlank() || selectedGroup != null || opponentFilter != null

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
            SearchAndFilterBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.searchQuery.value = it },
                selectedGroup = selectedGroup,
                onGroupSelected = { viewModel.selectedGroup.value = it },
                availableGroups = availableGroups
            )

            // Opponent filter row (hidden until opponents exist)
            if (opponents.isNotEmpty()) {
                OpponentFilterRow(
                    opponents = opponents,
                    selected = opponentFilter,
                    onSelect = viewModel::setOpponentFilter
                )
            }

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
                        if (hasActiveFilters) {
                            Text(
                                text = "No results match your search",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                viewModel.searchQuery.value = ""
                                viewModel.selectedGroup.value = null
                                viewModel.setOpponentFilter(null)
                            }) {
                                Text("Clear filters")
                            }
                        } else {
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
                                opponents = opponents,
                                toolbarState = markdownToolbarState
                            )
                            is HistoryItem.QuickGrade -> QuickGradeCard(
                                logWithCard = item.log,
                                viewModel = viewModel,
                                opponents = opponents,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
    availableGroups: List<String>
) {
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search cards...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true
        )

        if (availableGroups.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = groupDropdownExpanded,
                onExpandedChange = { groupDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedGroup ?: "All groups",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    label = { Text("Group") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupDropdownExpanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = groupDropdownExpanded,
                    onDismissRequest = { groupDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All groups") },
                        onClick = {
                            onGroupSelected(null)
                            groupDropdownExpanded = false
                        }
                    )
                    availableGroups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group) },
                            onClick = {
                                onGroupSelected(group)
                                groupDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Horizontal row of filter chips: All · Solo · each opponent. */
@Composable
private fun OpponentFilterRow(
    opponents: List<Opponent>,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") }
        )
        FilterChip(
            selected = selected == OPPONENT_FILTER_NONE,
            onClick = { onSelect(OPPONENT_FILTER_NONE) },
            label = { Text("Solo") }
        )
        opponents.forEach { opponent ->
            FilterChip(
                selected = selected == opponent.id,
                onClick = { onSelect(opponent.id) },
                label = { Text(opponent.name) }
            )
        }
    }
}

@Composable
fun SessionHistoryCard(
    session: PracticeSession,
    viewModel: HistoryViewModel,
    opponents: List<Opponent>,
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

    // Unique opponent names that appear in this session's logs, for a summary chip.
    val sessionOpponents = remember(reviewLogs, opponents) {
        reviewLogs.mapNotNull { it.reviewLog.opponentId }
            .distinct()
            .mapNotNull { id -> opponents.find { it.id == id }?.name }
    }

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

            // Opponent summary chip
            if (sessionOpponents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sessionOpponents.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                                opponents = opponents,
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
    opponents: List<Opponent>,
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

    val opponentLabel = opponentLabel(log.opponentId, opponents)

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
                            if (opponentLabel != null) append(" · vs ").append(opponentLabel)
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
                    opponents = opponents,
                    onSave = { notes, images ->
                        viewModel.updateReviewLogNotes(log, notes, images)
                        showNoteEditor = false
                    },
                    onOpponentChange = { opponentId ->
                        viewModel.updateReviewLogOpponent(log, opponentId)
                    },
                    onCreateOpponent = { name, mult ->
                        viewModel.createOpponent(name, mult)
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

/** Resolve a display string for an opponentId — null when there's nothing to show. */
private fun opponentLabel(opponentId: Long?, opponents: List<Opponent>): String? {
    if (opponentId == null) return null
    val match = opponents.find { it.id == opponentId }
    return match?.name ?: "[deleted]"
}

@Composable
private fun ReviewLogRow(
    logWithCard: ReviewLogWithCard,
    viewModel: HistoryViewModel,
    opponents: List<Opponent>,
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

    val opponentLabel = opponentLabel(log.opponentId, opponents)

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
                val metaText = buildString {
                    if (groupLabel != null) append(groupLabel)
                    if (opponentLabel != null) {
                        if (isNotEmpty()) append(" · ")
                        append("vs ").append(opponentLabel)
                    }
                }
                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
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
                opponents = opponents,
                onSave = { notes, images ->
                    viewModel.updateReviewLogNotes(log, notes, images)
                    showNoteEditor = false
                },
                onOpponentChange = { opponentId ->
                    viewModel.updateReviewLogOpponent(log, opponentId)
                },
                onCreateOpponent = { name, mult ->
                    viewModel.createOpponent(name, mult)
                },
                toolbarState = toolbarState
            )
        }
    }
}

/**
 * Inline note editor for a review log entry. Supports markdown notes, image attachments,
 * and reassigning the opponent (metadata-only — does not recompute scheduling).
 */
@Composable
private fun HistoryNoteEditor(
    reviewLog: ReviewLog,
    opponents: List<Opponent>,
    onSave: (String, List<String>) -> Unit,
    onOpponentChange: (Long?) -> Unit,
    onCreateOpponent: suspend (String, Double) -> Long,
    toolbarState: MarkdownToolbarState? = null
) {
    val context = LocalContext.current
    var notesValue by rememberSaveable(reviewLog.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(reviewLog.notes))
    }
    var noteImages by rememberSaveable(reviewLog.id) {
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

        OpponentPicker(
            selectedOpponentId = reviewLog.opponentId,
            opponents = opponents,
            onOpponentSelected = onOpponentChange,
            onCreate = onCreateOpponent
        )

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
