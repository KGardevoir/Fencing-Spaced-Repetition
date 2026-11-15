// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.components.AppIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GroupEditScreen(
    group: Group,
    learningGroups: List<LearningGroup>,
    onCreateLearningGroup: (name: String, onCreated: (Long) -> Unit) -> Unit = { _, _ -> },
    globalAutoShowAnswer: Boolean,
    onSave: (Group) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isNewGroup = group.id == 0L

    var name by rememberSaveable { mutableStateOf(group.name) }
    var description by rememberSaveable { mutableStateOf(group.description) }
    var learningGroupId by remember { mutableStateOf(group.learningGroupId) }

    // The one per-group setting (null = use global); everything else is the
    // learning group's.
    var overrideAutoShowAnswer by remember { mutableStateOf(group.autoShowAnswer != null) }
    var autoShowAnswer by remember { mutableStateOf(group.autoShowAnswer ?: globalAutoShowAnswer) }

    fun buildUpdatedGroup() = group.copy(
        name = name.trim(),
        description = description.trim(),
        learningGroupId = learningGroupId,
        autoShowAnswer = if (overrideAutoShowAnswer) autoShowAnswer else null
    )

    val isDirty = buildUpdatedGroup() != group
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

    // Compose Multiplatform's own BackHandler, not the one from
    // androidx.activity: same signature, and a real back gesture on Android.
    // Not the browser's Back button on the web, despite the symmetry -- there
    // Compose raises a back event for the Escape key alone and never touches
    // session history, so this guards the keyboard and the arrow above, and
    // browser Back still leaves the page.
    BackHandler(enabled = isDirty) {
        showUnsavedChangesDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewGroup) "Add Group" else "Edit Group") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) {
                            showUnsavedChangesDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(buildUpdatedGroup()) },
                        enabled = name.isNotBlank()
                    ) {
                        Text(if (isNewGroup) "Add" else "Save")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Group info section
            Text(
                "Group Info",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))

            LearningGroupPicker(
                learningGroups = learningGroups,
                selectedId = learningGroupId,
                onSelect = { learningGroupId = it },
                onCreate = onCreateLearningGroup
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Per-group settings section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Group Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Override the global setting for this group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Auto-show answer override
            SettingOverrideSection(
                label = "Auto-show Description",
                overridden = overrideAutoShowAnswer,
                onOverrideChange = { overrideAutoShowAnswer = it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = overrideAutoShowAnswer) { autoShowAnswer = !autoShowAnswer },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Show description immediately when viewing cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoShowAnswer,
                        onCheckedChange = { autoShowAnswer = it },
                        enabled = overrideAutoShowAnswer
                    )
                }
            }

            Text(
                "Cards per session, shuffling and scheduling are set on the learning group, " +
                    "on the Learning Groups screen; a Global group uses the Global settings there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
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

/** A setting with a tick to override the global default; shared with the learning group editor. */
@Composable
internal fun SettingOverrideSection(
    label: String,
    overridden: Boolean,
    onOverrideChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOverrideChange(!overridden) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (overridden) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Checkbox(
                checked = overridden,
                onCheckedChange = { onOverrideChange(it) }
            )
        }
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Which learning group a card group practises against. Global is the first
 * choice and the default. A new learning group can be made here, as a group
 * can from the card editor, and is selected as soon as it exists; renaming,
 * deleting and moving several groups at once is on the Learning Groups
 * screen, reached from the home screen.
 */
@Composable
private fun LearningGroupPicker(
    learningGroups: List<LearningGroup>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: (name: String, onCreated: (Long) -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    // The name of a learning group just created, shown until the list
    // catches up with it.
    var pendingName by remember { mutableStateOf<String?>(null) }
    val selected = learningGroups.find { it.id == selectedId }
    val selectedName = selected?.name ?: pendingName?.takeIf { selectedId != null }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AppIcons.LearningGroup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Learning Group", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (selectedName == null) "Global — shares learning progress with every Global group"
                    else "$selectedName — shares learning progress with the groups in it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                selectedName ?: "Global",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose learning group")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Global") },
                leadingIcon = { if (selectedId == null) Icon(Icons.Default.Check, contentDescription = null) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            learningGroups.forEach { learningGroup ->
                DropdownMenuItem(
                    text = { Text(learningGroup.name) },
                    leadingIcon = {
                        if (learningGroup.id == selectedId) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        onSelect(learningGroup.id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New learning group…") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                }
            )
        }
    }

    if (showCreateDialog) {
        var newName by rememberSaveable { mutableStateOf("") }
        val trimmed = newName.trim()
        val nameTaken = trimmed.equals("Global", ignoreCase = true) ||
            learningGroups.any { it.name.equals(trimmed, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            icon = { Icon(AppIcons.LearningGroup, contentDescription = null) },
            title = { Text("Create New Learning Group") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Learning Group Name") },
                    singleLine = true,
                    isError = nameTaken,
                    supportingText = if (nameTaken) {
                        { Text("That name is already in use") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreate(trimmed) { newId ->
                            pendingName = trimmed
                            onSelect(newId)
                            showCreateDialog = false
                        }
                    },
                    enabled = trimmed.isNotEmpty() && !nameTaken
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
