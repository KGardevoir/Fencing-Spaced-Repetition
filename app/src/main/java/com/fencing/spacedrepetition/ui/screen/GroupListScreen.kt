package com.fencing.spacedrepetition.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.ImportExportState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    groupViewModel: GroupViewModel,
    onNavigateBack: () -> Unit
) {
    val groups by groupViewModel.allGroups.collectAsState()
    val importExportState by groupViewModel.importExportState.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Group?>(null) }
    var editingGroup by remember { mutableStateOf<Group?>(null) }
    var groupForImport by remember { mutableStateOf<Group?>(null) }
    var groupForExport by remember { mutableStateOf<Group?>(null) }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            groupForImport?.let { group ->
                groupViewModel.importCardsToGroup(group.id, uri, context.contentResolver)
            }
        }
        groupForImport = null
    }

    // File picker for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            groupForExport?.let { group ->
                groupViewModel.exportGroupCards(group.id, uri, context.contentResolver)
            }
        }
        groupForExport = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Group")
            }
        }
    ) { paddingValues ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "No groups yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Tap + to create your first group",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    GroupListItem(
                        group = group,
                        dueCardCount = groupViewModel.getDueCardCountForGroup(group.id)
                            .collectAsState(initial = 0).value,
                        onEdit = { editingGroup = group },
                        onDelete = { showDeleteDialog = group },
                        onImport = {
                            groupForImport = group
                            importLauncher.launch(arrayOf("text/plain", "text/tab-separated-values", "*/*"))
                        },
                        onExport = {
                            groupForExport = group
                            exportLauncher.launch(groupViewModel.generateExportFilename(group.name))
                        }
                    )
                }
            }
        }
    }

    // Add group dialog
    if (showAddDialog) {
        AddEditGroupDialog(
            group = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, description, independentLearning ->
                groupViewModel.addGroup(name, description) { showAddDialog = false }
            }
        )
    }

    // Edit group dialog
    editingGroup?.let { group ->
        AddEditGroupDialog(
            group = group,
            onDismiss = { editingGroup = null },
            onConfirm = { name, description, independentLearning ->
                groupViewModel.updateGroup(group.copy(name = name, description = description, independentLearning = independentLearning)) {
                    editingGroup = null
                }
            }
        )
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { group ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Delete Group?") },
            text = {
                Text("Are you sure you want to delete \"${group.name}\"?\n\nCards in this group will NOT be deleted, only removed from this group.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        groupViewModel.deleteGroup(group)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import/Export state dialogs
    when (val state = importExportState) {
        is ImportExportState.Loading -> {
            AlertDialog(
                onDismissRequest = { },
                icon = { CircularProgressIndicator(modifier = Modifier.size(48.dp)) },
                title = { Text("Processing...") },
                text = { Text("Please wait while the operation completes.") },
                confirmButton = { }
            )
        }
        is ImportExportState.ImportSuccess -> {
            AlertDialog(
                onDismissRequest = { groupViewModel.resetImportExportState() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Import Complete") },
                text = {
                    Column {
                        Text("Successfully imported ${state.importedCount} cards.")
                        if (state.skippedCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${state.skippedCount} lines skipped due to errors:",
                                color = MaterialTheme.colorScheme.error
                            )
                            state.errors.take(5).forEach { error ->
                                Text("• $error", style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.errors.size > 5) {
                                Text("...and ${state.errors.size - 5} more", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { groupViewModel.resetImportExportState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.ExportSuccess -> {
            AlertDialog(
                onDismissRequest = { groupViewModel.resetImportExportState() },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Export Complete") },
                text = { Text("Successfully exported ${state.exportedCount} cards.") },
                confirmButton = {
                    Button(onClick = { groupViewModel.resetImportExportState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.Error -> {
            AlertDialog(
                onDismissRequest = { groupViewModel.resetImportExportState() },
                icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Error") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { groupViewModel.resetImportExportState() }) {
                        Text("OK")
                    }
                }
            )
        }
        is ImportExportState.Idle -> { /* No dialog */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListItem(
    group: Group,
    dueCardCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (group.independentLearning) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.School,
                            contentDescription = "Independent Learning",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (group.description.isNotEmpty()) {
                    Text(
                        group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group.independentLearning) {
                    Text(
                        "Independent learning enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (dueCardCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(dueCardCount.toString())
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import Cards") },
                        onClick = {
                            showMenu = false
                            onImport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Cards") },
                        onClick = {
                            showMenu = false
                            onExport()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddEditGroupDialog(
    group: Group?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, independentLearning: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var description by remember { mutableStateOf(group?.description ?: "") }
    var independentLearning by remember { mutableStateOf(group?.independentLearning ?: false) }
    val isEditing = group != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isEditing) Icons.Default.Edit else Icons.Default.Add,
                contentDescription = null
            )
        },
        title = { Text(if (isEditing) "Edit Group" else "Add Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = independentLearning,
                        onCheckedChange = { independentLearning = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Independent Learning", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Cards will have separate learning progress in this group",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), description.trim(), independentLearning) },
                enabled = name.isNotBlank()
            ) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
