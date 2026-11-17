// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.ui.components.AppIcons
import com.fencing.spacedrepetition.ui.components.SelectionAction
import com.fencing.spacedrepetition.ui.components.SelectionTopBar
import com.fencing.spacedrepetition.ui.components.rememberSelectionState

/**
 * The learning groups, and which card groups learn in each.
 *
 * Global is listed first and cannot be renamed or deleted: it is not a row
 * anywhere, only what a card group learns against when it is in no learning
 * group. Taking a group out of a learning group sends it back there. Its
 * settings are the app-wide defaults, which every learning group overrides
 * setting by setting.
 *
 * @param onEditLearningGroup opens the learning group editor; a learning
 *   group with id 0 is a new one.
 * @param onEditGlobal opens the Global settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningGroupsScreen(
    learningGroups: List<LearningGroup>,
    groups: List<Group>,
    onEditLearningGroup: (LearningGroup) -> Unit,
    onEditGlobal: () -> Unit,
    onDeleteLearningGroups: (List<LearningGroup>) -> Unit,
    onNavigateBack: () -> Unit
) {
    var deleting by remember { mutableStateOf<List<LearningGroup>?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val selection = rememberSelectionState(learningGroups.map { it.id })

    val globalGroups = groups.filter { group -> learningGroups.none { it.id == group.learningGroupId } }

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionTopBar(
                    selectedCount = selection.selected.size,
                    onExit = selection::exit,
                    onSelectAll = { selection.selectAll(learningGroups.map { it.id }) },
                    actions = listOf(
                        SelectionAction("Delete", Icons.Default.Delete, destructive = true) {
                            deleting = learningGroups.filter { it.id in selection.selected }
                        }
                    )
                )
            } else TopAppBar(
                title = { Text("Learning Groups") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Select Learning Groups") },
                                onClick = { showMenu = false; selection.start() },
                                enabled = learningGroups.isNotEmpty(),
                                leadingIcon = { Icon(Icons.Default.CheckBox, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditLearningGroup(LearningGroup(name = "")) }) {
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
                    description = "The default. Every card's own learning progress, " +
                        "scheduled by the app settings.",
                    members = globalGroups,
                    isGlobal = true,
                    // Global is not a row anywhere: it cannot be selected or deleted.
                    onClick = { if (!selection.active) onEditGlobal() }
                )
            }
            items(learningGroups, key = { it.id }) { learningGroup ->
                LearningGroupRow(
                    name = learningGroup.name,
                    description = learningGroup.description,
                    members = groups.filter { it.learningGroupId == learningGroup.id },
                    isGlobal = false,
                    selecting = selection.active,
                    selected = learningGroup.id in selection.selected,
                    onClick = {
                        if (selection.active) selection.toggle(learningGroup.id) else onEditLearningGroup(learningGroup)
                    },
                    onLongPress = { if (!selection.active) selection.start(learningGroup.id) }
                )
            }
            // Room for the last row above the action button.
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    deleting?.let { doomed ->
        val memberCount = groups.count { group -> doomed.any { it.id == group.learningGroupId } }
        val what = doomed.singleOrNull()?.let { "\"${it.name}\"" } ?: "${doomed.size} learning groups"
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(if (doomed.size == 1) "Delete Learning Group" else "Delete Learning Groups") },
            text = {
                Text(
                    "Delete $what? " +
                        (if (memberCount > 0) "$memberCount card group${if (memberCount == 1) "" else "s"} " +
                            "will go back to Global, and " else "") +
                        "the learning progress kept in ${if (doomed.size == 1) "it" else "them"} will be lost. " +
                        "Cards are not deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLearningGroups(doomed)
                        deleting = null
                        selection.exit()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LearningGroupRow(
    name: String,
    description: String,
    members: List<Group>,
    isGlobal: Boolean,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                isGlobal -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting && !isGlobal) Checkbox(checked = selected, onCheckedChange = { onClick() })
            else Icon(
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
        }
    }
}
