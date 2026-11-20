// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/** "You have unsaved changes": discard them and leave, or keep editing. */
@Composable
fun UnsavedChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved changes. Leaving now will discard them.") },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Discard Changes") }
        },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text("Keep Editing") } }
    )
}

/** Which Back presses a [rememberLeaveGuard] takes. */
enum class GuardedBack {
    /** None: the screen is a dialog, whose own dismissal handles Back. */
    NONE,
    /** Only while there are unsaved changes; otherwise Back goes where it would. */
    WHEN_DIRTY,
    /** Every one: the editor sits in place of another screen and must close itself. */
    ALWAYS
}

/**
 * Leaving an editor asks first when there are unsaved changes: returns the
 * "leave" action for the editor's back arrow, ✕ or Cancel, which leaves at
 * once ([onLeave]) when nothing has changed and otherwise shows the Unsaved
 * Changes dialog -- Discard Changes leaves, Keep Editing stays. [back] says
 * which Back presses go the same way.
 *
 * Back is Compose Multiplatform's own BackHandler: a real back gesture on
 * Android, but on the web only the Escape key -- Compose never touches the
 * browser's session history, so the browser's own Back still leaves the page.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberLeaveGuard(isDirty: Boolean, back: GuardedBack = GuardedBack.WHEN_DIRTY, onLeave: () -> Unit): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    val currentLeave by rememberUpdatedState(onLeave)
    val requestLeave: () -> Unit = { if (isDirty) asking = true else currentLeave() }
    BackHandler(enabled = back == GuardedBack.ALWAYS || (back == GuardedBack.WHEN_DIRTY && isDirty), onBack = requestLeave)
    if (asking) {
        UnsavedChangesDialog(
            onDiscard = { asking = false; currentLeave() },
            onKeepEditing = { asking = false }
        )
    }
    return requestLeave
}
