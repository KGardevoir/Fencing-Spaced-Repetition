// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One thing that can be done to the selected rows, offered in the selection
 * bar's ⋮ menu.
 */
data class SelectionAction(
    val label: String,
    val icon: ImageVector,
    /** Shown in the error colour, after a divider: deleting and the like. */
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Which rows of a list are selected, for the long-press selection pattern
 * every list screen uses:
 *
 * - Long-press a row to start selecting, with that row selected.
 * - While selecting, tapping a row selects or unselects it; Back or ✕ stops.
 * - The top bar becomes [SelectionTopBar]: ✕, "N selected", Select all, and a
 *   ⋮ menu holding every action -- Delete among them.
 */
@Stable
class SelectionState {
    var active by mutableStateOf(false)
        private set
    var selected by mutableStateOf(emptySet<Long>())
        private set

    fun start(id: Long? = null) {
        active = true
        selected = if (id == null) emptySet() else setOf(id)
    }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll(ids: Collection<Long>) {
        selected = ids.toSet()
    }

    fun exit() {
        active = false
        selected = emptySet()
    }

    /** Forgets selected rows that are gone from the list. */
    fun retain(ids: Collection<Long>) {
        val present = ids.toSet()
        if (!present.containsAll(selected)) selected = selected intersect present
    }
}

/** A [SelectionState] that drops rows as they leave [ids] and stops on Back. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberSelectionState(ids: Collection<Long>): SelectionState {
    val state = remember { SelectionState() }
    LaunchedEffect(ids) { state.retain(ids) }
    if (state.active) BackHandler(onBack = state::exit)
    return state
}

/**
 * The top bar while rows are selected: ✕ to stop, the count, Select all, and
 * every [actions] in a ⋮ menu so none is ever pushed off a narrow screen.
 * Actions are disabled while nothing is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    actions: List<SelectionAction>
) {
    var showMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onExit) { Icon(Icons.Default.Close, contentDescription = "Exit selection") }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = "Select all") }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Selection actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    val (ordinary, destructive) = actions.partition { !it.destructive }
                    ordinary.forEach { ActionItem(it, selectedCount > 0) { showMenu = false } }
                    if (ordinary.isNotEmpty() && destructive.isNotEmpty()) HorizontalDivider()
                    destructive.forEach { ActionItem(it, selectedCount > 0) { showMenu = false } }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    )
}

@Composable
private fun ActionItem(action: SelectionAction, enabled: Boolean, close: () -> Unit) {
    val color = if (action.destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
    DropdownMenuItem(
        text = { Text(action.label, color = if (enabled) color else color.copy(alpha = 0.38f)) },
        onClick = { close(); action.onClick() },
        enabled = enabled,
        leadingIcon = {
            Icon(action.icon, contentDescription = null, tint = if (action.destructive) color else LocalContentColor.current)
        }
    )
}
