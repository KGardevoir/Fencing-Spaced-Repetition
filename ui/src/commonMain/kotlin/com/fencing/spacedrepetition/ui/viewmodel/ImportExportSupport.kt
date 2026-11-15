// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

// The steps of an import or an export that both view models do identically.
//
// They were identical before too -- the same blocks, down to the wording of
// the error messages, in AndroidCardViewModel and AndroidGroupViewModel.
// Bringing the two into :ui was the moment to name the shared steps rather
// than copy them across a module boundary a third time.

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.ui.ImportFile
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ImageReader
import com.fencing.spacedrepetition.util.ImageStore
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.Time
import com.fencing.spacedrepetition.util.exportImageKeys
import com.fencing.spacedrepetition.util.ioDispatcher
import com.fencing.spacedrepetition.util.storeImages
import kotlinx.coroutines.withContext

/** What reading a chosen archive produced: cards to import, or a reason not to. */
internal sealed interface ArchiveContents {
    data class Cards(val parsed: List<ParsedCard>, val errors: List<String>) : ArchiveContents
    data class Failed(val message: String) : ArchiveContents
}

/**
 * Reads and parses an archive the user chose.
 *
 * A file with no cards is not the same as a file that would not open, and a
 * file of nothing but bad lines is a third thing again -- each gets its own
 * message, because "import failed" tells the user nothing they can act on.
 */
internal suspend fun readArchive(file: ImportFile): ArchiveContents {
    val text = file.text() ?: return ArchiveContents.Failed("Failed to open file for reading")
    val (parsed, errors) = withContext(ioDispatcher) { CardImportExport.parseCards(text.lines()) }
    return when {
        parsed.isEmpty() && errors.isEmpty() -> ArchiveContents.Failed("File is empty")
        parsed.isEmpty() -> ArchiveContents.Failed(
            "No valid cards found. Errors:\n${errors.joinToString("\n")}"
        )
        else -> ArchiveContents.Cards(parsed, errors)
    }
}

/**
 * Reads a chosen CSV and returns the state to show for it: the group prompt,
 * or why there is nothing to prompt about.
 *
 * A CSV carries no group of its own, so an import cannot finish here -- it
 * stops at [ImportExportState.CsvPendingGroupSelection] and waits for the
 * user to say where the cards go. The suggested name is derived from the
 * filename, which is the only hint the format leaves.
 */
internal suspend fun readCsvForGroupSelection(file: ImportFile): ImportExportState {
    val text = file.text() ?: return ImportExportState.Error("Failed to open file for reading")
    val (parsed, errors) = withContext(ioDispatcher) { CardImportExport.parseCsvCards(text) }
    return when {
        parsed.isEmpty() && errors.isEmpty() -> ImportExportState.Error("CSV file is empty")
        parsed.isEmpty() -> ImportExportState.Error(
            "No valid cards found in CSV. Errors:\n${errors.joinToString("\n")}"
        )
        else -> ImportExportState.CsvPendingGroupSelection(
            parsedCards = parsed,
            parseErrors = errors,
            suggestedGroupName = CardImportExport.deriveGroupNameFromFilename(file.name)
        )
    }
}

/** Where an import's cards go: group name -> id, and state context -> learning group id. */
internal data class ImportTargets(
    val groupIds: Map<String, Long>,
    val learningGroupIds: Map<String, Long>
)

/**
 * Creates every group and learning group an import mentions and applies the
 * settings the file carries for them, returning where the cards that follow
 * belong.
 *
 * A file from before learning groups names, for each extra state, the group
 * that learned independently. Each such group is put in a learning group of
 * its own name -- unless it is already in one, whose state it then fills --
 * so the states land somewhere that keeps them.
 */
internal suspend fun GroupRepository.groupsForImport(parsedCards: List<ParsedCard>): ImportTargets {
    val groupNames = parsedCards.flatMap { it.groupNames }.filter { it.isNotBlank() }.toSet()
    val stateRecords = parsedCards.filter { it.isGroupSpecificState }
    val legacyIndependent = stateRecords
        .filter { !it.learningGroupState }
        .mapNotNull { it.stateContext }
        .toSet()

    val groupIds = ensureGroupsExist(groupNames, legacyIndependent)

    CardImportExport.lastParsedLearningGroups.forEach { (name, description) ->
        ensureLearningGroup(name, description)
    }
    CardImportExport.lastParsedGroupSettings.forEach { (groupName, settings) ->
        val groupId = groupIds[groupName] ?: return@forEach
        val group = getGroupById(groupId) ?: return@forEach
        updateGroup(CardImportExport.applyGroupSettings(group, settings))
        settings[CardImportExport.LEARNING_GROUP_SETTING]?.let { learningGroupName ->
            setLearningGroup(groupId, ensureLearningGroup(learningGroupName))
        }
    }

    val learningGroupIds = buildMap {
        stateRecords.filter { it.learningGroupState }.mapNotNull { it.stateContext }.toSet()
            .forEach { name -> put(name, ensureLearningGroup(name)) }
        legacyIndependent.forEach { groupName ->
            groupIds[groupName]?.let { getGroupById(it) }?.learningGroupId?.let { put(groupName, it) }
        }
    }
    return ImportTargets(groupIds, learningGroupIds)
}

/**
 * Writes the cards of a CSV import into one group.
 *
 * A card whose question is already in the collection is updated rather than
 * duplicated, and keeps its pictures if the CSV brought none: a CSV is two or
 * three columns, so importing one is never a reason to lose an image that was
 * attached in the app.
 */
internal suspend fun importCsvCards(
    parsedCards: List<ParsedCard>,
    groupId: Long,
    cards: CardRepository,
    groups: GroupRepository,
    images: ImageStore
): Int {
    var imported = 0
    parsedCards.forEach { parsed ->
        val imageKeys = CardImportExport.storeImages(parsed.imageData, images)
        val existing = cards.findCardByQuestion(parsed.concept)
        if (existing != null) {
            cards.updateCard(
                existing.copy(
                    answer = parsed.answer,
                    imagePaths = imageKeys.ifEmpty { existing.imagePaths },
                    modified = Time.now()
                )
            )
            groups.addCardToGroup(existing.id, groupId)
        } else {
            val cardId = cards.insertCard(
                Card(
                    question = parsed.concept,
                    answer = parsed.answer,
                    imagePaths = imageKeys
                )
            )
            groups.addCardToGroup(cardId, groupId)
        }
        imported++
    }
    return imported
}

/** The reader an export of these rows will read its images through. */
internal suspend fun ImageStore.exportReader(
    cards: List<Card>,
    reviewLogs: List<ReviewLog> = emptyList(),
    sessions: List<PracticeSession> = emptyList()
): ImageReader = readerFor(exportImageKeys(cards, reviewLogs, sessions))

/** How an export's own report is shown. */
internal fun ExportResult.asImportExportState(): ImportExportState = when (this) {
    is ExportResult.Success -> ImportExportState.ExportSuccess(exportedCount)
    is ExportResult.Error -> ImportExportState.Error(message)
}
