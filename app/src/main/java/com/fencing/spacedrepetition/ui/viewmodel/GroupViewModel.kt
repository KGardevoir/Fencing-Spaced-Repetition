// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.GroupWithCards
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImportExportState {
    object Idle : ImportExportState()
    object Loading : ImportExportState()
    data class ImportSuccess(val importedCount: Int, val skippedCount: Int, val errors: List<String>) : ImportExportState()
    data class ExportSuccess(val exportedCount: Int) : ImportExportState()
    data class Error(val message: String) : ImportExportState()
    /** CSV import parsed cards and is waiting for the user to select/create a group */
    data class CsvPendingGroupSelection(
        val parsedCards: List<ParsedCard>,
        val parseErrors: List<String>,
        val suggestedGroupName: String
    ) : ImportExportState()
}

enum class GroupSortOption(val label: String) {
    NAME("Name"),
    CARD_COUNT("Card Count")
}

class GroupViewModel(
    application: Application,
    private val groupRepository: GroupRepository,
    private val cardRepository: CardRepository
) : AndroidViewModel(application) {

    val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groupSortOption = MutableStateFlow(GroupSortOption.NAME)
    val groupSortOption: StateFlow<GroupSortOption> = _groupSortOption.asStateFlow()

    fun setGroupSortOption(option: GroupSortOption) {
        _groupSortOption.value = option
    }

    val sortedGroups: StateFlow<List<Group>> = combine(
        groupRepository.getAllGroupsWithCards(),
        _groupSortOption
    ) { groupsWithCards, sortOption ->
        when (sortOption) {
            GroupSortOption.NAME -> groupsWithCards.map { it.group }.sortedBy { it.name.lowercase() }
            GroupSortOption.CARD_COUNT -> groupsWithCards.sortedByDescending { it.cards.size }.map { it.group }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupCount: StateFlow<Int> = groupRepository.getGroupCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedGroupForPractice = MutableStateFlow<Group?>(null)
    val selectedGroupForPractice: StateFlow<Group?> = _selectedGroupForPractice.asStateFlow()

    fun selectGroupForPractice(group: Group?) {
        _selectedGroupForPractice.value = group
    }

    fun addGroup(name: String, description: String = "", onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val group = Group(name = name, description = description)
                val newId = groupRepository.insertGroup(group)
                onSuccess(newId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun addGroupWithSettings(group: Group, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val newId = groupRepository.insertGroup(group)
                if (group.independentLearning) {
                    groupRepository.toggleIndependentLearning(newId, true)
                }
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateGroup(group: Group, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                groupRepository.updateGroup(group)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
        }
    }

    fun toggleIndependentLearning(groupId: Long, enabled: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                groupRepository.toggleIndependentLearning(groupId, enabled)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun getDueCardCountForGroup(groupId: Long): Flow<Int> =
        groupRepository.getDueCardCountByGroup(groupId)

    // Import/Export state
    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    fun exportGroupCards(groupId: Long, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithStates = withContext(Dispatchers.IO) {
                    groupRepository.getCardsByGroupWithStates(groupId)
                }

                if (cardsWithStates.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export in this group")
                    return@launch
                }

                // Collect all groups referenced by exported cards for settings
                val allGroupNames = cardsWithStates.flatMap { it.groupNames }.toSet()
                val allGroups = withContext(Dispatchers.IO) {
                    groupRepository.getAllGroupsSync().filter { it.name in allGroupNames }
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        // Wrap with GZIP compression
                        val outputStream = CardImportExport.createCompressedOutputStream(fileStream)
                        val exportResult = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream, allGroups)
                        outputStream.close()  // Ensure GZIP stream is properly closed
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

    fun importCardsToGroup(
        groupId: Long,
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

                // Check if this is a full format import with state
                val hasFullState = parsedCards.any { it.hasFullState }
                // Check if any cards have group-specific states (V2 format)
                val hasGroupSpecificStates = parsedCards.any { it.isGroupSpecificState }

                val importedCount = withContext(Dispatchers.IO) {
                    when {
                        hasGroupSpecificStates -> {
                            // V2 format with group-specific states (decode base64 images)
                            cardRepository.importCardsWithGroupStates(parsedCards, groupNameMap) {
                                CardImportExport.parsedCardToCard(getApplication(), it)
                            }
                        }
                        hasFullState -> {
                            // V1 full import with state - also add to target group (decode base64 images)
                            val cards = parsedCards.map { CardImportExport.parsedCardToCard(getApplication(), it) }
                            // Merge target group with any groups from file
                            val targetGroupName = groupRepository.getGroupById(groupId)?.name
                            val groupNamesPerCard = parsedCards.map { parsed ->
                                val fileGroups = parsed.groupNames.toMutableList()
                                // Add target group if not already present
                                if (targetGroupName != null && targetGroupName !in fileGroups) {
                                    fileGroups.add(targetGroupName)
                                }
                                fileGroups
                            }
                            cardRepository.importFullCards(cards, groupNamesPerCard, groupNameMap)
                        }
                        else -> {
                            // Simple import to specific group
                            val cardsToImport = parsedCards.map { it.concept to it.answer }
                            cardRepository.importCardsToGroup(cardsToImport, groupId, algorithm)
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

    fun generateExportFilename(groupName: String): String =
        CardImportExport.generateExportFilename(groupName)

    fun generateCsvExportFilename(groupName: String): String =
        CardImportExport.generateCsvExportFilename(groupName)

    /**
     * Step 1 of CSV import: parse the file and prompt the user for a group.
     * Sets state to CsvPendingGroupSelection with parsed cards and a suggested group name.
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
                    val cardsToImport = parsedCards.map { parsed ->
                        val decodedImagePaths = parsed.imageData.mapNotNull { base64Data ->
                            CardImportExport.decodeImageFromBase64(getApplication(), base64Data)
                        }
                        parsed.concept to Pair(parsed.answer, decodedImagePaths)
                    }

                    var count = 0
                    cardsToImport.forEach { (question, answerAndImages) ->
                        val (answer, imagePaths) = answerAndImages
                        val card = Card(
                            question = question,
                            answer = answer,
                            imagePaths = imagePaths,
                            algorithm = AlgorithmType.FSRS
                        )
                        // Check for duplicate by question
                        val existing = cardRepository.findCardByQuestion(question)
                        if (existing != null) {
                            // Update existing card and ensure it's in the group
                            val updated = existing.copy(
                                answer = answer,
                                imagePaths = if (imagePaths.isNotEmpty()) imagePaths else existing.imagePaths,
                                modified = Time.now()
                            )
                            cardRepository.updateCard(updated)
                            groupRepository.addCardToGroup(existing.id, groupId)
                        } else {
                            val cardId = cardRepository.insertCard(card)
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
     * Export a group's cards to CSV format.
     */
    fun exportGroupCardsCsv(groupId: Long, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithNames = withContext(Dispatchers.IO) {
                    groupRepository.getCardsByGroupWithGroupNames(groupId).map { (card, groupNames) ->
                        CardWithGroupNames(card, groupNames)
                    }
                }

                if (cardsWithNames.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export in this group")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        CardImportExport.exportCardsToCsv(cardsWithNames, fileStream)
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
}
