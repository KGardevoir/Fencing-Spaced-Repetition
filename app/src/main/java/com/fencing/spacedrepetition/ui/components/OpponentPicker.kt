package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.ui.screen.OpponentEditorDialog
import kotlinx.coroutines.launch

/**
 * Compact dropdown for selecting the opponent a review was performed against.
 * Includes a "None" option and an inline "Add new…" action that opens the opponent
 * editor dialog. New opponents are created via the provided [onCreate] suspend lambda
 * (which returns the new id, or -1 on duplicate name).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpponentPicker(
    selectedOpponentId: Long?,
    opponents: List<Opponent>,
    onOpponentSelected: (Long?) -> Unit,
    onCreate: suspend (name: String, skillMultiplier: Double) -> Long,
    modifier: Modifier = Modifier,
    label: String = "Opponent"
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val selected = opponents.find { it.id == selectedOpponentId }
    val displayText = when {
        selectedOpponentId == null -> "None"
        selected != null -> "${selected.name} · ×${"%.2f".format(selected.skillMultiplier)}"
        else -> "[deleted]"
    }

    Box(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        onOpponentSelected(null)
                        expanded = false
                    }
                )
                if (opponents.isNotEmpty()) {
                    HorizontalDivider()
                }
                opponents.forEach { opponent ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(opponent.name)
                                Text(
                                    "×${"%.2f".format(opponent.skillMultiplier)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onOpponentSelected(opponent.id)
                            expanded = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Add new…") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        expanded = false
                        showCreate = true
                    }
                )
            }
        }
    }

    if (showCreate) {
        OpponentEditorDialog(
            initial = null,
            onDismiss = {
                showCreate = false
                createError = null
            },
            onSave = { name, mult, _ ->
                scope.launch {
                    val id = onCreate(name, mult)
                    if (id < 0) {
                        createError = "An opponent named \"$name\" already exists."
                    } else {
                        onOpponentSelected(id)
                        showCreate = false
                        createError = null
                    }
                }
            },
            errorMessage = createError
        )
    }
}
