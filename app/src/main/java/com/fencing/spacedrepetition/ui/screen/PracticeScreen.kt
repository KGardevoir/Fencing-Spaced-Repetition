package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.ui.components.CardImagesDisplay
import com.fencing.spacedrepetition.ui.viewmodel.PracticeUiState
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel

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
            onSave = { question, answer ->
                viewModel.updateCardText(currentCardIndex, question, answer)
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
                        Icon(Icons.Default.ArrowBack, "Back")
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
    cardNumber: Int,
    totalCards: Int,
    autoShowAnswer: Boolean = false,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)?,
    onFinish: () -> Unit
) {
    var showAnswer by remember(sessionCard.card.id, autoShowAnswer) { mutableStateOf(autoShowAnswer) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = cardNumber.toFloat() / totalCards.toFloat(),
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

        // Card content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    Text(
                        text = "Answer:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sessionCard.card.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
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
                        Text("Show Answer")
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
                        showAnswer = autoShowAnswer
                        onPrevious()
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
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
                        showAnswer = autoShowAnswer
                        onNext()
                    }
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
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

@Composable
fun EditCardDialog(
    card: Card,
    onDismiss: () -> Unit,
    onSave: (question: String, answer: String) -> Unit
) {
    var question by remember(card.id) { mutableStateOf(card.question) }
    var answer by remember(card.id) { mutableStateOf(card.answer) }

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
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Answer") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(question, answer) },
                        enabled = question.isNotBlank() && answer.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
