// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.components.AppIcons

/**
 * The learning groups, and which card groups learn in each.
 *
 * Global is listed first and cannot be edited or deleted: it is not a row
 * anywhere, only what a card group learns against when it is in no learning
 * group. Taking a group out of a learning group sends it back there.
 *
 * @param onSaveLearningGroup creates the learning group when its id is 0 and
 *   updates it otherwise; the set is the card groups that should be in it
 *   afterwards, so a group left out of it goes back to Global.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningGroupsScreen(
    learningGroups: List<LearningGroup>,
    groups: List<Group>,
    onSaveLearningGroup: (LearningGroup, Set<Long>) -> Unit,
    onDeleteLearningGroup: (LearningGroup) -> Unit,
    onNavigateBack: () -> Unit
) {
    var editing by remember { mutableStateOf<LearningGroup?>(null) }
    var deleting by remember { mutableStateOf<LearningGroup?>(null) }

    val globalGroups = groups.filter { group -> learningGroups.none { it.id == group.learningGroupId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning Groups") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = LearningGroup(name = "") }) {
                Icon(Icons.Default.Add, contentDescription = "Add Learning Group")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Card groups in the same learning group share one set of learning " +
                        "progress: grading a card in one moves it on in all of them. " +
                        "A group in no learning group learns Global.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                LearningGroupRow(
                    name = "Global",
                    description = "The default. Every card's own learning progress.",
                    members = globalGroups,
                    isGlobal = true
                )
            }
            items(learningGroups, key = { it.id }) { learningGroup ->
                LearningGroupRow(
                    name = learningGroup.name,
                    description = learningGroup.description,
                    members = groups.filter { it.learningGroupId == learningGroup.id },
                    isGlobal = false,
                    onEdit = { editing = learningGroup },
                    onDelete = { deleting = learningGroup }
                )
            }
            // Room for the last row's buttons above the action button.
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    editing?.let { learningGroup ->
        LearningGroupEditDialog(
            learningGroup = learningGroup,
            learningGroups = learningGroups,
            groups = groups,
            onSave = { updated, memberIds ->
                onSaveLearningGroup(updated, memberIds)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { learningGroup ->
        val memberCount = groups.count { it.learningGroupId == learningGroup.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Learning Group") },
            text = {
                Text(
                    "Delete \"${learningGroup.name}\"? " +
                        (if (memberCount > 0) "Its $memberCount group${if (memberCount == 1) "" else "s"} " +
                            "will go back to Global, and " else "") +
                        "the learning progress kept in it will be lost. Cards are not deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLearningGroup(learningGroup)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LearningGroupRow(
    name: String,
    description: String,
    members: List<Group>,
    isGlobal: Boolean,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGlobal) Modifier else Modifier.clickable(onClick = onEdit)),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlobal) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isGlobal) Icons.Default.Public else AppIcons.LearningGroup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (members.isEmpty()) "No groups"
                    else members.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isGlobal) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit $name")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete $name")
                }
            }
        }
    }
}

/**
 * A learning group's name and description, and a tick per card group for
 * whether it is in. A group already in a different learning group says so,
 * since ticking it moves it here.
 */
@Composable
private fun LearningGroupEditDialog(
    learningGroup: LearningGroup,
    learningGroups: List<LearningGroup>,
    groups: List<Group>,
    onSave: (LearningGroup, Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = learningGroup.id == 0L
    var name by rememberSaveable { mutableStateOf(learningGroup.name) }
    var description by rememberSaveable { mutableStateOf(learningGroup.description) }
    var memberIds by remember {
        mutableStateOf(
            if (isNew) emptySet()
            else groups.filter { it.learningGroupId == learningGroup.id }.map { it.id }.toSet()
        )
    }
    val nameTaken = learningGroups.any {
        it.id != learningGroup.id && it.name.equals(name.trim(), ignoreCase = true)
    } || name.trim().equals("Global", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.LearningGroup, contentDescription = null) },
        title = { Text(if (isNew) "New Learning Group" else "Edit Learning Group") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text("That name is already in use") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Card groups",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (groups.isEmpty()) {
                    Text(
                        "No card groups yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                groups.forEach { group ->
                    val checked = group.id in memberIds
                    val elsewhere = learningGroups
                        .find { it.id == group.learningGroupId && it.id != learningGroup.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                memberIds = if (checked) memberIds - group.id else memberIds + group.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                memberIds = if (it) memberIds + group.id else memberIds - group.id
                            }
                        )
                        Column {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            if (elsewhere != null) {
                                Text(
                                    if (checked) "Moves here from ${elsewhere.name}" else "In ${elsewhere.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(learningGroup.copy(name = name.trim(), description = description.trim()), memberIds)
                },
                enabled = name.isNotBlank() && !nameTaken
            ) { Text(if (isNew) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
