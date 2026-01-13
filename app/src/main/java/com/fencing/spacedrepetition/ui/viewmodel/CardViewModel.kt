package com.fencing.spacedrepetition.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardViewModel(
    application: Application,
    private val repository: CardRepository,
    private val groupRepository: GroupRepository
) : AndroidViewModel(application) {

    val allCards: StateFlow<List<Card>> = repository.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCardsWithGroups: StateFlow<List<CardWithGroups>> = repository.getAllCardsWithGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueCardCount: StateFlow<Int> = repository.getDueCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cardCount: StateFlow<Int> = repository.getCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Groups for filtering
    val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupFilter = MutableStateFlow<Group?>(null)
    val selectedGroupFilter: StateFlow<Group?> = _selectedGroupFilter.asStateFlow()

    val filteredCards: StateFlow<List<Card>> = combine(
        allCardsWithGroups,
        selectedGroupFilter
    ) { cardsWithGroups, group ->
        if (group == null) {
            cardsWithGroups.map { it.card }
        } else {
            cardsWithGroups
                .filter { cardWithGroups -> cardWithGroups.groups.any { it.id == group.id } }
                .map { it.card }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGroupFilter(group: Group?) {
        _selectedGroupFilter.value = group
    }

    fun getGroupsForCard(cardId: Long): Flow<List<Group>> =
        groupRepository.getGroupsForCard(cardId)

    fun getLearningStatesForCard(cardId: Long): Flow<List<CardGroupLearningState>> =
        groupRepository.getAllLearningStatesForCard(cardId)

    fun updateLearningState(learningState: CardGroupLearningState, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateLearningState(learningState)
            onComplete()
        }
    }

    fun getDueCardCountByGroup(groupId: Long): Flow<Int> =
        repository.getDueCardCountByGroup(groupId)

    fun getCardCountByGroup(groupId: Long): Flow<Int> =
        repository.getCardCountByGroup(groupId)

    fun addCard(
        question: String,
        answer: String,
        groupIds: List<Long>,
        algorithm: AlgorithmType,
        imagePaths: List<String> = emptyList(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val card = Card(
                    question = question,
                    answer = answer,
                    algorithm = algorithm,
                    imagePaths = imagePaths
                )
                repository.insertCardWithGroups(card, groupIds)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateCard(card: Card, groupIds: List<Long>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateCard(card.copy(modified = System.currentTimeMillis()))
                repository.updateCardGroups(card.id, groupIds)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }

    // Selection state for bulk operations
    private val _selectedCardIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCardIds: StateFlow<Set<Long>> = _selectedCardIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedCardIds.value = emptySet()
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedCardIds.value = emptySet()
    }

    fun toggleCardSelection(cardId: Long) {
        _selectedCardIds.value = if (cardId in _selectedCardIds.value) {
            _selectedCardIds.value - cardId
        } else {
            _selectedCardIds.value + cardId
        }
    }

    fun selectAllCards() {
        _selectedCardIds.value = filteredCards.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedCardIds.value = emptySet()
    }

    // Bulk operations
    fun deleteSelectedCards(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToDelete = _selectedCardIds.value.toList()
            idsToDelete.forEach { cardId ->
                repository.deleteCardById(cardId)
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    fun updateSelectedCardsGroups(groupIds: List<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToUpdate = _selectedCardIds.value.toList()
            idsToUpdate.forEach { cardId ->
                repository.updateCardGroups(cardId, groupIds)
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    fun resetSelectedCardsState(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToReset = _selectedCardIds.value.toList()
            idsToReset.forEach { cardId ->
                repository.resetCardState(cardId)
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    fun resetCardState(cardId: Long, resetGroupStates: Boolean = false, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetCardState(cardId, resetGroupStates)
            onComplete()
        }
    }

    fun resetCardStateInGroup(cardId: Long, groupId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetCardStateInGroup(cardId, groupId)
            onComplete()
        }
    }

    // Import/Export state
    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    fun exportAllCards(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithGroups = withContext(Dispatchers.IO) {
                    repository.getAllCardsWithGroupNames()
                }

                if (cardsWithGroups.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export")
                    return@launch
                }

                val exportData = cardsWithGroups.map { (card, groupNames) ->
                    CardWithGroupNames(card, groupNames)
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        CardImportExport.exportCardsWithGroups(exportData, outputStream)
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
                        // Wrap with GZIP decompression
                        val inputStream = CardImportExport.createDecompressedInputStream(fileStream)
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

                // Ensure all groups exist (creates missing ones automatically)
                val groupNameMap = withContext(Dispatchers.IO) {
                    groupRepository.ensureGroupsExist(allGroupNames)
                }

                // Check if this is a full format import
                val hasFullState = parsedCards.any { it.hasFullState }

                val importedCount = withContext(Dispatchers.IO) {
                    if (hasFullState) {
                        // Full import with state and groups (decode base64 images)
                        val cards = parsedCards.map { CardImportExport.parsedCardToCard(getApplication(), it) }
                        val groupNamesPerCard = parsedCards.map { it.groupNames }
                        repository.importFullCards(cards, groupNamesPerCard, groupNameMap)
                    } else {
                        // Simple import
                        val cardsToImport = parsedCards.map { it.question to it.answer }
                        repository.importCards(cardsToImport, algorithm)
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
}
