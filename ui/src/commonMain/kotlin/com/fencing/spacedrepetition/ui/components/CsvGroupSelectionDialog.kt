// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group

/**
 * Dialog for selecting or creating a group during CSV import.
 * Shows a text field pre-populated with a name derived from the filename,
 * and a list of existing groups to select from.
 */
@Composable
fun CsvGroupSelectionDialog(
    suggestedGroupName: String,
    existingGroups: List<Group>,
    cardCount: Int,
    onConfirm: (Long) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by rememberSaveable { mutableStateOf(suggestedGroupName) }
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var useExistingGroup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.TableChart, contentDescription = null) },
        title = { Text("Import $cardCount Cards") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Choose a group for the imported cards:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Create new group
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useExistingGroup = false },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !useExistingGroup,
                        onClick = { useExistingGroup = false }
                    )
                    Text(
                        "Create new group",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!useExistingGroup) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 40.dp, top = 4.dp, bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Option 2: Use existing group
                if (existingGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useExistingGroup = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useExistingGroup,
                            onClick = { useExistingGroup = true }
                        )
                        Text(
                            "Use existing group",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (useExistingGroup) {
                        Column(
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                        ) {
                            existingGroups.forEach { group ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedGroupId = group.id }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedGroupId == group.id,
                                        onClick = { selectedGroupId = group.id }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            group.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (group.description.isNotBlank()) {
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
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (useExistingGroup && selectedGroupId != null) {
                        onConfirm(selectedGroupId!!)
                    } else if (!useExistingGroup && groupName.isNotBlank()) {
                        // Check if group name matches an existing group
                        val matchingGroup = existingGroups.find {
                            it.name.equals(groupName.trim(), ignoreCase = true)
                        }
                        if (matchingGroup != null) {
                            onConfirm(matchingGroup.id)
                        } else {
                            onCreateGroup(groupName.trim())
                        }
                    }
                },
                enabled = if (useExistingGroup) selectedGroupId != null else groupName.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
