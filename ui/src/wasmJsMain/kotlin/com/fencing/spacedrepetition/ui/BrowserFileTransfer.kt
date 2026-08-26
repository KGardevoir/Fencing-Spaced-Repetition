// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ParsedCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

/**
 * [FileTransfer] in a browser: a hidden file input in, a download out.
 *
 * The import and export themselves are not here. They are in the two view
 * models, shared with Android, and all this does is answer the two questions
 * a browser answers differently -- which file, and where does the result go.
 *
 * In :ui rather than in :web, unlike BrowserImagePicker, for a reason worth
 * stating: this module's tests run in a real browser engine, and the gzip
 * round trip underneath it is exactly the kind of thing that compiles, ships,
 * and then does not work. See BrowserFilesTest.
 */
fun browserFileTransfer(
    scope: CoroutineScope,
    cards: CardViewModel,
    groups: GroupViewModel
): FileTransfer = BrowserFileTransfer(scope, cards, groups)

private class BrowserFileTransfer(
    private val scope: CoroutineScope,
    private val cards: CardViewModel,
    private val groups: GroupViewModel
) : FileTransfer {

    override fun importCards() = choose(ARCHIVE) { cards.importCards(it) }

    override fun exportAllCards(includeHistory: Boolean) =
        cards.exportAllCards(archiveDownload("all_cards.tsv.gz"), includeHistory)

    override fun exportGroups(groupIds: List<Long>, includeHistory: Boolean) =
        cards.exportSelectedGroups(
            groupIds,
            archiveDownload("selected_groups_cards.tsv.gz"),
            includeHistory
        )

    override fun importCardsCsv() = choose(CSV) { cards.csvImportParseFile(it) }

    override fun exportAllCardsCsv() = cards.exportAllCardsCsv(csvDownload("all_cards.csv"))

    override fun exportGroupsCsv(groupIds: List<Long>) =
        cards.exportSelectedGroupsCsv(groupIds, csvDownload("selected_groups_cards.csv"))

    override fun importIntoGroup(group: Group) =
        choose(ARCHIVE) { groups.importCardsToGroup(group.id, it) }

    override fun exportGroup(group: Group) =
        groups.exportGroupCards(group.id, archiveDownload(groups.generateExportFilename(group.name)))

    override fun importCsvIntoGroup(group: Group) = choose(CSV) { groups.csvImportParseFile(it) }

    override fun exportGroupCsv(group: Group) =
        groups.exportGroupCardsCsv(group.id, csvDownload(groups.generateCsvExportFilename(group.name)))

    override fun csvImportInto(parsed: List<ParsedCard>, errors: List<String>, groupId: Long) =
        cards.csvImportComplete(parsed, errors, groupId)

    override fun csvImportIntoNewGroup(
        parsed: List<ParsedCard>,
        errors: List<String>,
        groupName: String
    ) {
        groups.addGroup(groupName) { newGroupId ->
            cards.csvImportComplete(parsed, errors, newGroupId)
        }
    }

    /**
     * Opens the chooser now and hands what comes back to [use].
     *
     * The dialog is opened before the coroutine starts, not inside it -- see
     * [openFileDialog]. The coroutine is only there to wait for the answer,
     * and a dismissed dialog is simply the end of it, as on Android, where
     * the result callback arrives with no Uri.
     */
    private fun choose(accept: String, use: (ImportFile) -> Unit) {
        val chosen = openFileDialog(accept)
        scope.launch {
            val file = chosen.await<JsAny?>() ?: return@launch
            use(BrowserImportFile(file))
        }
    }

    private companion object {
        /**
         * No filter for archives: an export of this app's is a `.tsv.gz`,
         * which no two systems agree on a type for, and the Android picker is
         * open to everything for the same reason. A CSV is the one thing they
         * do agree on, so that one is hinted at.
         */
        const val ARCHIVE = ""
        const val CSV = ".csv,text/csv,text/plain"
    }
}

private class BrowserImportFile(private val file: JsAny) : ImportFile {
    override val name: String get() = fileName(file)
    override suspend fun text(): String? = blobText(file)
}

/**
 * An export the browser downloads once the formatting has run.
 *
 * The whole file is built in memory first, which Android's does not have to
 * do -- there is no way to stream into a download that works in every browser
 * this targets, and the blob has to exist before the link can point at it.
 */
private class BrowserExportFile(
    private val filename: String,
    private val mime: String,
    private val compress: Boolean
) : ExportFile {

    override suspend fun write(content: (Appendable) -> ExportResult): ExportResult {
        val text = StringBuilder()
        val result = content(text)
        if (result is ExportResult.Error) return result

        val failure = downloadText(filename, text.toString(), mime, compress)
        return if (failure == null) result else ExportResult.Error("Failed to save file: $failure")
    }
}

/** internal, not private: a backup is one of these too -- see DownloadBackups. */
internal fun archiveDownload(filename: String): ExportFile =
    BrowserExportFile(filename, "application/gzip", compress = true)

private fun csvDownload(filename: String): ExportFile =
    BrowserExportFile(filename, "text/csv", compress = false)
