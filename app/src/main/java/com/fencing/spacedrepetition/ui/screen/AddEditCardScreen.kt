package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCardScreen(
    viewModel: CardViewModel,
    groupViewModel: GroupViewModel,
    cardToEdit: Card? = null,
    onNavigateBack: () -> Unit
) {
    var question by remember { mutableStateOf(cardToEdit?.question ?: "") }
    var answer by remember { mutableStateOf(cardToEdit?.answer ?: "") }
    var selectedAlgorithm by remember { mutableStateOf(cardToEdit?.algorithm ?: AlgorithmType.FSRS) }

    // Group selection state
    val allGroups by groupViewModel.allGroups.collectAsState()
    val cardGroups = cardToEdit?.let { card ->
        viewModel.getGroupsForCard(card.id).collectAsState(initial = emptyList()).value
    } ?: emptyList()

    var selectedGroupIds by remember(cardGroups) {
        mutableStateOf(cardGroups.map { it.id }.toSet())
    }

    var showGroupSelectionSheet by remember { mutableStateOf(false) }

    val isEditing = cardToEdit != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Card" else "Add New Card") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                onValueChange = { question = it },
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
                onValueChange = { answer = it },
                label = { Text("Answer / Explanation") },
                placeholder = { Text("e.g., Front foot pointed forward, back foot at 90°...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                },
                minLines = 3,
                maxLines = 8
            )

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
                            onClick = { selectedAlgorithm = AlgorithmType.FSRS }
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
                            onClick = { selectedAlgorithm = AlgorithmType.SM2 }
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

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Button(
                onClick = {
                    if (question.isNotBlank() && answer.isNotBlank()) {
                        if (isEditing && cardToEdit != null) {
                            viewModel.updateCard(
                                cardToEdit.copy(
                                    question = question,
                                    answer = answer,
                                    algorithm = selectedAlgorithm
                                ),
                                groupIds = selectedGroupIds.toList(),
                                onSuccess = onNavigateBack
                            )
                        } else {
                            viewModel.addCard(
                                question = question,
                                answer = answer,
                                groupIds = selectedGroupIds.toList(),
                                algorithm = selectedAlgorithm,
                                onSuccess = onNavigateBack
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = question.isNotBlank() && answer.isNotBlank()
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
}
