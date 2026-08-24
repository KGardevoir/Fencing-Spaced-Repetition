// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.launch
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.ui.components.CardImagesDisplay
import com.fencing.spacedrepetition.ui.components.CardImagesEdit
import com.fencing.spacedrepetition.ui.components.MarkdownDescriptionField
import com.fencing.spacedrepetition.ui.components.MarkdownKeyboardToolbar
import com.fencing.spacedrepetition.ui.components.MarkdownText
import com.fencing.spacedrepetition.ui.components.rememberMarkdownToolbarState
import androidx.compose.ui.text.input.TextFieldValue
import com.fencing.spacedrepetition.ui.viewmodel.PracticeUiState
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToGrading: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionCards by viewModel.sessionCards.collectAsState()
    val currentCardIndex by viewModel.currentCardIndex.collectAsState()
    val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }

    // Edit dialog
    if (showEditDialog && sessionCards.isNotEmpty() && currentCardIndex < sessionCards.size) {
        EditCardDialog(
            card = sessionCards[currentCardIndex].card,
            onDismiss = { showEditDialog = false },
            onSave = { question, answer, imagePaths ->
                viewModel.updateCardComplete(currentCardIndex, question, answer, imagePaths)
                showEditDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Session") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState is PracticeUiState.Practicing && sessionCards.isNotEmpty()) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, "Edit Card")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                is PracticeUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is PracticeUiState.NoCards -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No cards due for review!",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add more cards or come back later",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }

                is PracticeUiState.Practicing -> {
                    if (sessionCards.isNotEmpty() && currentCardIndex < sessionCards.size) {
                        PracticeCardView(
                            sessionCard = sessionCards[currentCardIndex],
                            nextSessionCard = if (currentCardIndex + 1 < sessionCards.size) {
                                sessionCards[currentCardIndex + 1]
                            } else null,
                            previousSessionCard = if (currentCardIndex > 0) {
                                sessionCards[currentCardIndex - 1]
                            } else null,
                            cardNumber = currentCardIndex + 1,
                            totalCards = sessionCards.size,
                            autoShowAnswer = autoShowAnswer,
                            onNext = { viewModel.nextCard() },
                            onPrevious = if (currentCardIndex > 0) {
                                { viewModel.previousCard() }
                            } else null,
                            onFinish = { viewModel.finishPractice() }
                        )
                    }
                }

                is PracticeUiState.ReadyToGrade -> {
                    LaunchedEffect(Unit) {
                        onNavigateToGrading()
                    }
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
                            text = "Error",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (uiState as PracticeUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
fun PracticeCardView(
    sessionCard: SessionCard,
    nextSessionCard: SessionCard?,
    previousSessionCard: SessionCard?,
    cardNumber: Int,
    totalCards: Int,
    autoShowAnswer: Boolean = false,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)?,
    onFinish: () -> Unit
) {
    var showAnswer by remember(sessionCard.card.id, autoShowAnswer) { mutableStateOf(autoShowAnswer) }

    // Swipe gesture state
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val swipeThreshold = 300f // Minimum drag distance to trigger navigation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = { cardNumber.toFloat() / totalCards.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Text(
            text = "Card $cardNumber of $totalCards",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card stack container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Next card (underneath when swiping left)
            nextSessionCard?.let { nextCard ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .graphicsLayer {
                            // Calculate progress of left swipe (0 to 1)
                            val swipeProgress = (offsetX.value / -swipeThreshold).coerceIn(0f, 1f)

                            // Scale from 0.92 to 1.0 as current card is swiped away
                            scaleX = 0.92f + (0.08f * swipeProgress)
                            scaleY = 0.92f + (0.08f * swipeProgress)

                            // Move up from 16dp to 0dp as current card is swiped away
                            translationY = 16.dp.toPx() * (1f - swipeProgress)

                            // Fade from 0.6 to 1.0 alpha, but only show when swiping left
                            alpha = if (offsetX.value < 0) {
                                0.6f + (0.4f * swipeProgress)
                            } else {
                                0f
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Question preview
                        Text(
                            text = "Question:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = nextCard.card.question,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Previous card (underneath when swiping right)
            previousSessionCard?.let { prevCard ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .graphicsLayer {
                            // Calculate progress of right swipe (0 to 1)
                            val swipeProgress = (offsetX.value / swipeThreshold).coerceIn(0f, 1f)

                            // Scale from 0.92 to 1.0 as current card is swiped away
                            scaleX = 0.92f + (0.08f * swipeProgress)
                            scaleY = 0.92f + (0.08f * swipeProgress)

                            // Move up from 16dp to 0dp as current card is swiped away
                            translationY = 16.dp.toPx() * (1f - swipeProgress)

                            // Fade from 0.6 to 1.0 alpha, but only show when swiping right
                            alpha = if (offsetX.value > 0) {
                                0.6f + (0.4f * swipeProgress)
                            } else {
                                0f
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Question preview
                        Text(
                            text = "Question:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = prevCard.card.question,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Current card (on top)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = offsetX.value
                        rotationZ = offsetX.value / 40f // Slight rotation during swipe
                    }
                    .pointerInput(sessionCard.card.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        // Swipe left (next card)
                                        offsetX.value < -swipeThreshold && cardNumber < totalCards -> {
                                            offsetX.animateTo(
                                                targetValue = -2000f,
                                                animationSpec = tween(300)
                                            )
                                            showAnswer = autoShowAnswer
                                            onNext()
                                            offsetX.snapTo(0f)
                                        }
                                        // Swipe right (previous card)
                                        offsetX.value > swipeThreshold && onPrevious != null -> {
                                            offsetX.animateTo(
                                                targetValue = 2000f,
                                                animationSpec = tween(300)
                                            )
                                            showAnswer = autoShowAnswer
                                            onPrevious()
                                            offsetX.snapTo(0f)
                                        }
                                        // Not enough swipe distance - snap back
                                        else -> {
                                            offsetX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = tween(300)
                                            )
                                        }
                                    }
                                }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                scope.launch {
                                    val newOffset = offsetX.value + dragAmount
                                    // Limit swipe range to prevent excessive dragging
                                    val limitedOffset = newOffset.coerceIn(-1000f, 1000f)
                                    offsetX.snapTo(limitedOffset)
                                }
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Question
                    Text(
                        text = "Question:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sessionCard.card.question,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (showAnswer) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "Description:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MarkdownText(
                            text = sessionCard.card.answer,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Display images if available
                        if (sessionCard.card.imagePaths.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            CardImagesDisplay(
                                imagePaths = sessionCard.card.imagePaths,
                                modifier = Modifier.fillMaxWidth(),
                                maxHeight = 200
                            )
                        }

                        if (sessionCard.card.category.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text(sessionCard.card.category) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Category,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    } else {
                        Button(
                            onClick = { showAnswer = true },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Show Description")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous button
            if (onPrevious != null) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // Animate card to the right
                            offsetX.animateTo(
                                targetValue = 2000f,
                                animationSpec = tween(300)
                            )
                            showAnswer = autoShowAnswer
                            onPrevious()
                            offsetX.snapTo(0f)
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Next/Finish button
            if (cardNumber < totalCards) {
                Button(
                    onClick = {
                        scope.launch {
                            // Animate card to the left
                            offsetX.animateTo(
                                targetValue = -2000f,
                                animationSpec = tween(300)
                            )
                            showAnswer = autoShowAnswer
                            onNext()
                            offsetX.snapTo(0f)
                        }
                    }
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            } else {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Grade Cards")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Done, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCardDialog(
    card: Card,
    onDismiss: () -> Unit,
    onSave: (question: String, answer: String, imagePaths: List<String>) -> Unit
) {
    var question by rememberSaveable(card.id) { mutableStateOf(card.question) }
    var answerFieldValue by rememberSaveable(card.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(card.answer))
    }
    var imagePaths by rememberSaveable(card.id) { mutableStateOf(card.imagePaths.toMutableList()) }
    val markdownToolbarState = rememberMarkdownToolbarState()

    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copy image to app's internal storage and save path
            val savedPath = saveImageToInternalStorage(context, it)
            if (savedPath != null) {
                imagePaths = (imagePaths + savedPath).toMutableList()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Edit Card",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Concept") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                MarkdownDescriptionField(
                    value = answerFieldValue,
                    onValueChange = { answerFieldValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    toolbarState = markdownToolbarState
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Images section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Images",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (imagePaths.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CardImagesEdit(
                                imagePaths = imagePaths,
                                onRemoveImage = { path ->
                                    imagePaths = imagePaths.filter { it != path }.toMutableList()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxHeight = 120
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "No images",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                MarkdownKeyboardToolbar(markdownToolbarState)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(question, answerFieldValue.text, imagePaths) },
                        enabled = question.isNotBlank() && answerFieldValue.text.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
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
        val timestamp = Time.now()
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
