// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.FileImageReader
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.createCompressedOutputStream
import com.fencing.spacedrepetition.util.decodeImageFromBase64
import com.fencing.spacedrepetition.util.exportCardsToCsv
import com.fencing.spacedrepetition.util.exportCardsWithGroupStates
import com.fencing.spacedrepetition.util.openSmartInputStream
import com.fencing.spacedrepetition.util.parseCards
import com.fencing.spacedrepetition.util.parseCsvCards
import com.fencing.spacedrepetition.util.parsedCardToCard
import com.fencing.spacedrepetition.util.parsedReviewLogsToEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The half of card import and export that needs a file.
 *
 * Split from CardViewModel rather than kept with it, because every method
 * here takes a Uri and a ContentResolver. Those are Android, so this is
 * Android; everything that merely manipulates cards stayed behind in :ui and
 * runs in a browser.
 *
 * A subclass rather than a helper holding a view model, so the screens and
 * the navigation graph see one object with one lifetime -- the split is a
 * fact about where the code can compile, not something the caller should
 * have to arrange around.
 */
class AndroidCardViewModel(
    private val application: Application,
    repository: CardRepository,
    groupRepository: GroupRepository,
    opponentRepository: OpponentRepository
) : CardViewModel(repository, groupRepository, opponentRepository) {

    fun exportAllCards(uri: Uri, contentResolver: ContentResolver, includeHistory: Boolean = false) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithStates = withContext(Dispatchers.IO) {
                    repository.getAllCardsWithGroupStates()
                }

                if (cardsWithStates.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export")
                    return@launch
                }

                // Collect all groups referenced by exported cards for settings
                val allGroupNames = cardsWithStates.flatMap { it.groupNames }.toSet()
                val allGroups = withContext(Dispatchers.IO) {
                    groupRepository.getAllGroupsSync().filter { it.name in allGroupNames }
                }

                val reviewLogs = if (includeHistory) withContext(Dispatchers.IO) {
                    repository.getAllReviewLogsSync()
                } else emptyList()

                val cardQuestions = if (includeHistory) {
                    cardsWithStates.associate { it.card.id to it.card.question }
                } else emptyMap()

                // Bundle opponents whenever history is included so review logs can
                // round-trip their opponent assignments by name.
                val opponents = if (includeHistory) withContext(Dispatchers.IO) {
                    opponentRepository.getAllOpponentsSync()
                } else emptyList()
                val opponentNamesById = opponents.associate { it.id to it.name }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        val outputStream = CardImportExport.createCompressedOutputStream(fileStream)
                        val exportResult = CardImportExport.exportCardsWithGroupStates(
                            cardsWithStates, outputStream, allGroups, reviewLogs, cardQuestions,
                            opponents, opponentNamesById, FileImageReader(application)
                        )
                        outputStream.close()
                        exportResult
                    } ?: ExportResult.Error("Failed to open file for writing")
                }

                _importExportState.value = when (result) {
                    is ExportResult.Success -> ImportExportState.ExportSuccess(result.exportedCount)
                    is ExportResult.Error -> ImportExportState.Error(result.message)
                }
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun exportSelectedGroups(selectedGroupIds: List<Long>, uri: Uri, contentResolver: ContentResolver, includeHistory: Boolean = false) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                // Get all cards with states
                val allCardsWithStates = withContext(Dispatchers.IO) {
                    repository.getAllCardsWithGroupStates()
                }

                // Get selected group names for filtering
                val selectedGroups = withContext(Dispatchers.IO) {
                    groupRepository.getAllGroupsSync().filter { it.id in selectedGroupIds }
                }
                val selectedGroupNames = selectedGroups.map { it.name }.toSet()

                if (selectedGroupNames.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No groups selected")
                    return@launch
                }

                // Filter cards that belong to at least one of the selected groups
                val filteredCardsWithStates = allCardsWithStates.filter { cardWithState ->
                    cardWithState.groupNames.any { it in selectedGroupNames }
                }

                if (filteredCardsWithStates.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards found in selected groups")
                    return@launch
                }

                val exportedCardIds = filteredCardsWithStates.map { it.card.id }.toSet()
                val reviewLogs = if (includeHistory) withContext(Dispatchers.IO) {
                    repository.getAllReviewLogsSync().filter { it.cardId in exportedCardIds }
                } else emptyList()

                val cardQuestions = if (includeHistory) {
                    filteredCardsWithStates.associate { it.card.id to it.card.question }
                } else emptyMap()

                // Only export opponents that are actually referenced by the included logs,
                // to avoid leaking unrelated opponents from the user's roster.
                val referencedOpponentIds = reviewLogs.mapNotNull { it.opponentId }.toSet()
                val opponents = if (referencedOpponentIds.isNotEmpty()) withContext(Dispatchers.IO) {
                    opponentRepository.getAllOpponentsSync()
                        .filter { it.id in referencedOpponentIds }
                } else emptyList()
                val opponentNamesById = opponents.associate { it.id to it.name }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        val outputStream = CardImportExport.createCompressedOutputStream(fileStream)
                        val exportResult = CardImportExport.exportCardsWithGroupStates(
                            filteredCardsWithStates, outputStream, selectedGroups, reviewLogs, cardQuestions,
                            opponents, opponentNamesById, FileImageReader(application)
                        )
                        outputStream.close()
                        exportResult
                    } ?: ExportResult.Error("Failed to open file for writing")
                }

                _importExportState.value = when (result) {
                    is ExportResult.Success -> ImportExportState.ExportSuccess(result.exportedCount)
                    is ExportResult.Error -> ImportExportState.Error(result.message)
                }
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun importCards(
        uri: Uri,
        contentResolver: ContentResolver,
        algorithm: AlgorithmType = AlgorithmType.FSRS
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val parseResult = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { fileStream ->
                        val inputStream = CardImportExport.smartInputStream(fileStream)
                        val result = CardImportExport.parseCards(inputStream)
                        inputStream.close()
                        result
                    }
                }

                if (parseResult == null) {
                    _importExportState.value = ImportExportState.Error("Failed to open file for reading")
                    return@launch
                }

                val (parsedCards, parseErrors) = parseResult

                if (parsedCards.isEmpty() && parseErrors.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("File is empty")
                    return@launch
                }

                if (parsedCards.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No valid cards found. Errors:\n${parseErrors.joinToString("\n")}")
                    return@launch
                }

                // Collect all unique group names from parsed cards
                val allGroupNames = parsedCards
                    .flatMap { it.groupNames }
                    .filter { it.isNotBlank() }
                    .toSet()

                // Detect which groups have independent learning (groups with state-specific rows)
                val groupsWithIndependentLearning = parsedCards
                    .filter { it.isGroupSpecificState }
                    .mapNotNull { it.stateContext }
                    .toSet()

                // Ensure all groups exist (creates missing ones automatically)
                val groupNameMap = withContext(Dispatchers.IO) {
                    groupRepository.ensureGroupsExist(allGroupNames, groupsWithIndependentLearning)
                }

                // Apply group settings from export file
                val parsedGroupSettings = CardImportExport.lastParsedGroupSettings
                if (parsedGroupSettings.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        parsedGroupSettings.forEach { (groupName, settings) ->
                            val groupIdForSettings = groupNameMap[groupName] ?: return@forEach
                            val group = groupRepository.getGroupById(groupIdForSettings) ?: return@forEach
                            val updatedGroup = CardImportExport.applyGroupSettings(group, settings)
                            groupRepository.updateGroup(updatedGroup)
                        }
                    }
                }

                // Check if this is a full format import
                val hasFullState = parsedCards.any { it.hasFullState }
                // Check if any cards have group-specific states (V2/V3 format)
                val hasGroupSpecificStates = parsedCards.any { it.isGroupSpecificState }

                val importedCount = withContext(Dispatchers.IO) {
                    when {
                        hasGroupSpecificStates -> {
                            // V2/V3 format with group-specific states
                            repository.importCardsWithGroupStates(parsedCards, groupNameMap) {
                                CardImportExport.parsedCardToCard(application, it)
                            }
                        }
                        hasFullState -> {
                            // V1 full import with state and groups (decode base64 images)
                            val cards = parsedCards.map { CardImportExport.parsedCardToCard(application, it) }
                            val groupNamesPerCard = parsedCards.map { it.groupNames }
                            repository.importFullCards(cards, groupNamesPerCard, groupNameMap)
                        }
                        else -> {
                            // Simple import
                            val cardsToImport = parsedCards.map { it.concept to it.answer }
                            repository.importCards(cardsToImport, algorithm)
                        }
                    }
                }

                // Restore opponents (creates missing ones; existing names keep local values)
                val parsedOpponents = CardImportExport.lastParsedOpponents
                val opponentNameToId = if (parsedOpponents.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        opponentRepository.ensureOpponentsExist(
                            parsedOpponents.map { Triple(it.name, it.skillMultiplier, it.notes) }
                        )
                    }
                } else emptyMap()

                // Import review history if present in the file
                val parsedReviewHistory = CardImportExport.lastParsedReviewHistory
                if (parsedReviewHistory.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val questionToCardId = repository.getAllCardsSync()
                            .associate { it.question to it.id }
                        val reviewLogs = CardImportExport.parsedReviewLogsToEntities(
                            application, parsedReviewHistory, questionToCardId, opponentNameToId
                        )
                        if (reviewLogs.isNotEmpty()) {
                            repository.importReviewLogs(reviewLogs)
                        }
                    }
                }

                _importExportState.value = ImportExportState.ImportSuccess(
                    importedCount = importedCount,
                    skippedCount = parseErrors.size,
                    errors = parseErrors
                )
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Import failed: ${e.message}")
            }
        }
    }

    // ========== CSV Import/Export ==========

    /**
     * Step 1 of CSV import: parse the file and prompt the user for a group.
     */
    fun csvImportParseFile(
        uri: Uri,
        contentResolver: ContentResolver,
        filename: String
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val parseResult = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { fileStream ->
                        CardImportExport.parseCsvCards(fileStream)
                    }
                }

                if (parseResult == null) {
                    _importExportState.value = ImportExportState.Error("Failed to open file for reading")
                    return@launch
                }

                val (parsedCards, parseErrors) = parseResult

                if (parsedCards.isEmpty() && parseErrors.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("CSV file is empty")
                    return@launch
                }

                if (parsedCards.isEmpty()) {
                    _importExportState.value = ImportExportState.Error(
                        "No valid cards found in CSV. Errors:\n${parseErrors.joinToString("\n")}"
                    )
                    return@launch
                }

                val suggestedName = CardImportExport.deriveGroupNameFromFilename(filename)
                _importExportState.value = ImportExportState.CsvPendingGroupSelection(
                    parsedCards = parsedCards,
                    parseErrors = parseErrors,
                    suggestedGroupName = suggestedName
                )
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV import failed: ${e.message}")
            }
        }
    }

    /**
     * Step 2 of CSV import: user has selected/created a group. Complete the import.
     */
    fun csvImportComplete(
        parsedCards: List<ParsedCard>,
        parseErrors: List<String>,
        groupId: Long
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val importedCount = withContext(Dispatchers.IO) {
                    var count = 0
                    parsedCards.forEach { parsed ->
                        val decodedImagePaths = parsed.imageData.mapNotNull { base64Data ->
                            CardImportExport.decodeImageFromBase64(application, base64Data)
                        }

                        val existing = repository.findCardByQuestion(parsed.concept)
                        if (existing != null) {
                            val updated = existing.copy(
                                answer = parsed.answer,
                                imagePaths = if (decodedImagePaths.isNotEmpty()) decodedImagePaths else existing.imagePaths,
                                modified = Time.now()
                            )
                            repository.updateCard(updated)
                            groupRepository.addCardToGroup(existing.id, groupId)
                        } else {
                            val card = Card(
                                question = parsed.concept,
                                answer = parsed.answer,
                                imagePaths = decodedImagePaths,
                                algorithm = AlgorithmType.FSRS
                            )
                            val cardId = repository.insertCard(card)
                            groupRepository.addCardToGroup(cardId, groupId)
                        }
                        count++
                    }
                    count
                }

                _importExportState.value = ImportExportState.ImportSuccess(
                    importedCount = importedCount,
                    skippedCount = parseErrors.size,
                    errors = parseErrors
                )
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV import failed: ${e.message}")
            }
        }
    }

    /**
     * Export all cards to CSV format.
     */
    fun exportAllCardsCsv(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithGroups = withContext(Dispatchers.IO) {
                    repository.getAllCardsWithGroupNames().map { (card, groupNames) ->
                        CardWithGroupNames(card, groupNames)
                    }
                }

                if (cardsWithGroups.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        CardImportExport.exportCardsToCsv(cardsWithGroups, fileStream, FileImageReader(application))
                    } ?: ExportResult.Error("Failed to open file for writing")
                }

                _importExportState.value = when (result) {
                    is ExportResult.Success -> ImportExportState.ExportSuccess(result.exportedCount)
                    is ExportResult.Error -> ImportExportState.Error(result.message)
                }
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV export failed: ${e.message}")
            }
        }
    }

    /**
     * Export selected groups' cards to CSV format.
     */
    fun exportSelectedGroupsCsv(selectedGroupIds: List<Long>, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val selectedGroups = withContext(Dispatchers.IO) {
                    groupRepository.getAllGroupsSync().filter { it.id in selectedGroupIds }
                }
                val selectedGroupNames = selectedGroups.map { it.name }.toSet()

                if (selectedGroupNames.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No groups selected")
                    return@launch
                }

                val allCardsWithGroups = withContext(Dispatchers.IO) {
                    repository.getAllCardsWithGroupNames().map { (card, groupNames) ->
                        CardWithGroupNames(card, groupNames)
                    }
                }

                val filteredCards = allCardsWithGroups.filter { cwg ->
                    cwg.groupNames.any { it in selectedGroupNames }
                }

                if (filteredCards.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards found in selected groups")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        CardImportExport.exportCardsToCsv(filteredCards, fileStream, FileImageReader(application))
                    } ?: ExportResult.Error("Failed to open file for writing")
                }

                _importExportState.value = when (result) {
                    is ExportResult.Success -> ImportExportState.ExportSuccess(result.exportedCount)
                    is ExportResult.Error -> ImportExportState.Error(result.message)
                }
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV export failed: ${e.message}")
            }
        }
    }}
