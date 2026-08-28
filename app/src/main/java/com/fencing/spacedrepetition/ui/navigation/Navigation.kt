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
import android.content.Context
import com.fencing.spacedrepetition.BuildConfig
import com.fencing.spacedrepetition.BuildInfo
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.App
import com.fencing.spacedrepetition.ui.BinaryExportFile
import com.fencing.spacedrepetition.ui.FileTransfer
import com.fencing.spacedrepetition.ui.screen.getFilenameFromUri
import com.fencing.spacedrepetition.ui.ExportFile
import com.fencing.spacedrepetition.ui.ImportFile
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.createCompressedOutputStream
import com.fencing.spacedrepetition.util.smartInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    cardViewModel: CardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: GroupViewModel,
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
 *
 * What happens to the chosen document is not here. A Uri becomes an
 * [ImportFile] or an [ExportFile] and the view models -- shared with the
 * browser build -- do the reading, parsing, formatting and writing.
 */
@Composable
private fun rememberAndroidFileTransfer(
    cardViewModel: CardViewModel,
    groupViewModel: GroupViewModel
): FileTransfer {
    val context = LocalContext.current

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
        uri?.let { cardViewModel.importCards(importFile(context, it)) }
    }

    val archiveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let {
            val destination = exportFile(context, it, compress = true)
            val groupIds = archiveGroupIds
            if (groupIds == null) {
                cardViewModel.exportAllCards(destination, archiveIncludesHistory)
            } else {
                cardViewModel.exportSelectedGroups(groupIds, destination, archiveIncludesHistory)
            }
        }
    }

    val csvImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { cardViewModel.csvImportParseFile(importFile(context, it)) }
    }

    val csvExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            val destination = exportFile(context, it, compress = false)
            val groupIds = csvGroupIds
            if (groupIds == null) {
                cardViewModel.exportAllCardsCsv(destination)
            } else {
                cardViewModel.exportSelectedGroupsCsv(groupIds, destination)
            }
        }
    }

    val photoExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let { cardViewModel.exportAllPhotos(binaryExportFile(context, it)) }
    }

    val groupImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            groupForImport?.let { g ->
                groupViewModel.importCardsToGroup(g.id, importFile(context, uri))
            }
        }
        groupForImport = null
    }

    val groupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        uri?.let {
            groupForExport?.let { g ->
                groupViewModel.exportGroupCards(g.id, exportFile(context, uri, compress = true))
            }
        }
        groupForExport = null
    }

    val groupCsvImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (groupForCsvImport != null) {
                groupViewModel.csvImportParseFile(importFile(context, uri))
            }
        }
        groupForCsvImport = null
    }

    val groupCsvExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            groupForCsvExport?.let { g ->
                groupViewModel.exportGroupCardsCsv(g.id, exportFile(context, uri, compress = false))
            }
        }
        groupForCsvExport = null
    }

    return remember(cardViewModel, groupViewModel) {
        object : FileTransfer {

            override fun importCards() = archiveImport.launch(ARCHIVE_TYPES)

            override fun exportAllCards(includeHistory: Boolean) {
                archiveGroupIds = null
                archiveIncludesHistory = includeHistory
                archiveExport.launch(CardImportExport.generateAllCardsFilename())
            }

            override fun exportGroups(groupIds: List<Long>, includeHistory: Boolean) {
                archiveGroupIds = groupIds
                archiveIncludesHistory = includeHistory
                archiveExport.launch(CardImportExport.generateSelectedGroupsFilename())
            }

            override fun importCardsCsv() = csvImport.launch(CSV_TYPES)

            override fun exportAllCardsCsv() {
                csvGroupIds = null
                csvExport.launch(CardImportExport.generateAllCardsCsvFilename())
            }

            override fun exportGroupsCsv(groupIds: List<Long>) {
                csvGroupIds = groupIds
                csvExport.launch(CardImportExport.generateSelectedGroupsCsvFilename())
            }

            override fun exportAllPhotos() =
                photoExport.launch(CardImportExport.generateAllPhotosFilename())

            override fun importIntoGroup(group: Group) {
                groupForImport = group
                groupImport.launch(ARCHIVE_TYPES)
            }

            override fun exportGroup(group: Group) {
                groupForExport = group
                groupExport.launch(groupViewModel.generateExportFilename(group.name))
            }

            override fun importCsvIntoGroup(group: Group) {
                groupForCsvImport = group
                groupCsvImport.launch(CSV_TYPES)
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

// Both lists end in */*, and have to: an export of this app's is a .yaml.gz
// and a document provider may report it as anything or as nothing, so a
// picker that took the named types literally would grey out the very file the
// user came to choose.
//
// The tab-separated types are still named because a backup taken before the
// format moved to YAML still imports, and a provider that does report a type
// will report that one for it.
private val ARCHIVE_TYPES = arrayOf(
    "application/gzip", "application/x-gzip", "text/plain",
    "application/yaml", "text/yaml", "text/x-yaml",
    "text/tab-separated-values", "application/octet-stream", "*/*"
)

private val CSV_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "text/plain",
    "application/octet-stream", "*/*"
)

/**
 * A chosen document, written whole.
 *
 * No streaming, unlike [exportFile]: what goes through here is a zip, whose
 * central directory is only known once every entry has been written, so the
 * archive is built in memory either way. See [BinaryExportFile].
 */
private fun binaryExportFile(context: Context, uri: Uri): BinaryExportFile =
    object : BinaryExportFile {

        override suspend fun write(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: return@withContext "could not open the file for writing"
                stream.use { it.write(bytes) }
                null
            } catch (e: Exception) {
                e.message ?: "the file could not be written"
            }
        }
    }

/**
 * A chosen document, read as text.
 *
 * Gzip is detected rather than assumed, by [CardImportExport.smartInputStream]
 * reading the first two bytes: an export of this app's is compressed and a
 * file someone wrote by hand is not, and both are offered by the same picker.
 */
private fun importFile(context: Context, uri: Uri): ImportFile = object : ImportFile {

    override val name: String = getFilenameFromUri(context, uri) ?: "import"

    override suspend fun text(): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                CardImportExport.smartInputStream(stream)
                    .bufferedReader(Charsets.UTF_8)
                    .readText()
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * A chosen document, written to as the export produces it.
 *
 * The writer goes straight to the document -- through gzip, for an archive --
 * so a collection with a hundred photographs in it is never held in memory as
 * one string. That is the reason [ExportFile] takes the formatting rather
 * than the finished text.
 */
private fun exportFile(context: Context, uri: Uri, compress: Boolean): ExportFile =
    object : ExportFile {

        override suspend fun write(content: (Appendable) -> ExportResult): ExportResult =
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        if (compress) {
                            val gzip = CardImportExport.createCompressedOutputStream(stream)
                            val writer = gzip.bufferedWriter(Charsets.UTF_8)
                            val result = content(writer)
                            // Flushed and finished by hand rather than with use:
                            // the gzip trailer is written by close, and closing
                            // the writer alone would leave the stream truncated.
                            writer.flush()
                            gzip.close()
                            result
                        } else {
                            stream.bufferedWriter(Charsets.UTF_8).use { writer -> content(writer) }
                        }
                    } ?: ExportResult.Error("Failed to open file for writing")
                } catch (e: Exception) {
                    ExportResult.Error("Failed to write file: ${e.message}")
                }
            }
    }
