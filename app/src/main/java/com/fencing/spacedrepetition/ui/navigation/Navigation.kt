// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.fencing.spacedrepetition.BuildConfig
import com.fencing.spacedrepetition.BuildInfo
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.App
import com.fencing.spacedrepetition.ui.FileTransfer
import com.fencing.spacedrepetition.ui.screen.getFilenameFromUri
import com.fencing.spacedrepetition.ui.viewmodel.AndroidCardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.AndroidGroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import com.fencing.spacedrepetition.util.ParsedCard

/**
 * The Android app, which is the shared app plus the things only Android can do.
 *
 * The routing used to live here as an androidx.navigation graph. It is in :ui
 * now, shared with the browser build, and what is left on this side is what
 * genuinely could not cross: the storage-framework file pickers, resolving a
 * tree URI to a folder name, and opening a link in a browser.
 */
@Composable
fun AppNavigation(
    cardViewModel: AndroidCardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: AndroidGroupViewModel,
    settingsViewModel: SettingsViewModel,
    historyViewModel: HistoryViewModel,
    opponentViewModel: OpponentViewModel
) {
    val context = LocalContext.current
    val autoBackupUri by settingsViewModel.autoBackupUri.collectAsState()

    val backupFolderName = autoBackupUri?.let { uriString ->
        DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsViewModel.setAutoBackupUri(uri.toString())
        }
    }

    App(
        cardViewModel = cardViewModel,
        practiceViewModel = practiceViewModel,
        groupViewModel = groupViewModel,
        settingsViewModel = settingsViewModel,
        historyViewModel = historyViewModel,
        opponentViewModel = opponentViewModel,
        buildInfo = BuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            gitCommit = BuildConfig.GIT_COMMIT
        ),
        transfer = rememberAndroidFileTransfer(cardViewModel, groupViewModel),
        onOpenLink = { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        backupFolderName = backupFolderName,
        onPickBackupFolder = { folderPickerLauncher.launch(null) }
    )
}

/**
 * File transfer through the storage access framework.
 *
 * Every picker is created once here rather than per button, and what the
 * result is *for* is recorded just before launching -- an activity result
 * arrives with nothing but a Uri, so the intent behind it has to be
 * remembered on this side. Single flight is safe: the picker is full-screen
 * system UI, so a second request cannot start while one is open.
 */
@Composable
private fun rememberAndroidFileTransfer(
    cardViewModel: AndroidCardViewModel,
    groupViewModel: AndroidGroupViewModel
): FileTransfer {
    val context = LocalContext.current
    val resolver = context.contentResolver

    var archiveIncludesHistory by remember { mutableStateOf(false) }
    var archiveGroupIds by remember { mutableStateOf<List<Long>?>(null) }
    var csvGroupIds by remember { mutableStateOf<List<Long>?>(null) }
    var groupForImport by remember { mutableStateOf<Group?>(null) }
    var groupForExport by remember { mutableStateOf<Group?>(null) }
    var groupForCsvImport by remember { mutableStateOf<Group?>(null) }
    var groupForCsvExport by remember { mutableStateOf<Group?>(null) }

    val archiveImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { cardViewModel.importCards(it, resolver) }
    }

    val archiveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let {
            val groupIds = archiveGroupIds
            if (groupIds == null) {
                cardViewModel.exportAllCards(it, resolver, archiveIncludesHistory)
            } else {
                cardViewModel.exportSelectedGroups(groupIds, it, resolver, archiveIncludesHistory)
            }
        }
    }

    val csvImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val filename = getFilenameFromUri(context, it) ?: "import.csv"
            cardViewModel.csvImportParseFile(it, resolver, filename)
        }
    }

    val csvExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            val groupIds = csvGroupIds
            if (groupIds == null) {
                cardViewModel.exportAllCardsCsv(it, resolver)
            } else {
                cardViewModel.exportSelectedGroupsCsv(groupIds, it, resolver)
            }
        }
    }

    val groupImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { groupForImport?.let { g -> groupViewModel.importCardsToGroup(g.id, uri, resolver) } }
        groupForImport = null
    }

    val groupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let { groupForExport?.let { g -> groupViewModel.exportGroupCards(g.id, uri, resolver) } }
        groupForExport = null
    }

    val groupCsvImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (groupForCsvImport != null) {
                val filename = getFilenameFromUri(context, uri) ?: "import.csv"
                groupViewModel.csvImportParseFile(uri, resolver, filename)
            }
        }
        groupForCsvImport = null
    }

    val groupCsvExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { groupForCsvExport?.let { g -> groupViewModel.exportGroupCardsCsv(g.id, uri, resolver) } }
        groupForCsvExport = null
    }

    return remember(cardViewModel, groupViewModel) {
        object : FileTransfer {

            override fun importCards() = archiveImport.launch(
                arrayOf(
                    "application/gzip", "application/x-gzip", "text/plain",
                    "text/tab-separated-values", "application/octet-stream", "*/*"
                )
            )

            override fun exportAllCards(includeHistory: Boolean) {
                archiveGroupIds = null
                archiveIncludesHistory = includeHistory
                archiveExport.launch("all_cards.tsv.gz")
            }

            override fun exportGroups(groupIds: List<Long>, includeHistory: Boolean) {
                archiveGroupIds = groupIds
                archiveIncludesHistory = includeHistory
                archiveExport.launch("selected_groups_cards.tsv.gz")
            }

            override fun importCardsCsv() = csvImport.launch(
                arrayOf(
                    "text/csv", "text/comma-separated-values", "text/plain",
                    "application/octet-stream", "*/*"
                )
            )

            override fun exportAllCardsCsv() {
                csvGroupIds = null
                csvExport.launch("all_cards.csv")
            }

            override fun exportGroupsCsv(groupIds: List<Long>) {
                csvGroupIds = groupIds
                csvExport.launch("selected_groups_cards.csv")
            }

            override fun importIntoGroup(group: Group) {
                groupForImport = group
                groupImport.launch(
                    arrayOf(
                        "application/gzip", "application/x-gzip", "text/plain",
                        "text/tab-separated-values", "application/octet-stream", "*/*"
                    )
                )
            }

            override fun exportGroup(group: Group) {
                groupForExport = group
                groupExport.launch(groupViewModel.generateExportFilename(group.name))
            }

            override fun importCsvIntoGroup(group: Group) {
                groupForCsvImport = group
                groupCsvImport.launch(
                    arrayOf(
                        "text/csv", "text/comma-separated-values", "text/plain",
                        "application/octet-stream", "*/*"
                    )
                )
            }

            override fun exportGroupCsv(group: Group) {
                groupForCsvExport = group
                groupCsvExport.launch(groupViewModel.generateCsvExportFilename(group.name))
            }

            override fun csvImportInto(
                parsed: List<ParsedCard>,
                errors: List<String>,
                groupId: Long
            ) = cardViewModel.csvImportComplete(parsed, errors, groupId)

            override fun csvImportIntoNewGroup(
                parsed: List<ParsedCard>,
                errors: List<String>,
                groupName: String
            ) {
                groupViewModel.addGroup(groupName) { newGroupId ->
                    cardViewModel.csvImportComplete(parsed, errors, newGroupId)
                }
            }
        }
    }
}
