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
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.util.CardImportExport
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredCards: StateFlow<List<Card>> = combine(
        allCardsWithGroups,
        selectedGroupFilter,
        searchQuery
    ) { cardsWithGroups, group, query ->
        var filtered = if (group == null) {
            cardsWithGroups.map { it.card }
        } else {
            cardsWithGroups
                .filter { cardWithGroups -> cardWithGroups.groups.any { it.id == group.id } }
                .map { it.card }
        }

        // Apply search filter
        if (query.isNotBlank()) {
            val searchLower = query.lowercase()
            filtered = filtered.filter { card ->
                card.question.lowercase().contains(searchLower) ||
                card.answer.lowercase().contains(searchLower)
            }
        }

        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGroupFilter(group: Group?) {
        _selectedGroupFilter.value = group
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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

    fun resetSelectedCardsGlobalState(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToReset = _selectedCardIds.value.toList()
            idsToReset.forEach { cardId ->
                repository.resetCardState(cardId, resetGroupStates = false)
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    fun resetSelectedCardsInGroups(groupIds: Set<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToReset = _selectedCardIds.value.toList()
            idsToReset.forEach { cardId ->
                groupIds.forEach { groupId ->
                    repository.resetCardStateInGroup(cardId, groupId)
                }
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    fun resetSelectedCardsBothStates(groupIds: Set<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToReset = _selectedCardIds.value.toList()
            idsToReset.forEach { cardId ->
                // Reset global state
                repository.resetCardState(cardId, resetGroupStates = false)
                // Reset specific group states
                groupIds.forEach { groupId ->
                    repository.resetCardStateInGroup(cardId, groupId)
                }
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

    fun gradeCard(cardId: Long, grade: Grade, groupId: Long? = null, onComplete: (Card) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val card = repository.getCardById(cardId) ?: return@launch
                val updatedCard = if (groupId != null) {
                    repository.reviewCardWithGroup(card, grade, groupId)
                } else {
                    repository.reviewCard(card, grade)
                }
                onComplete(updatedCard)
            } catch (e: Exception) {
                // Handle error
            }
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

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { fileStream ->
                        val outputStream = CardImportExport.createCompressedOutputStream(fileStream)
                        val exportResult = CardImportExport.exportCardsWithGroupStates(
                            cardsWithStates, outputStream, allGroups
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
                            repository.importCardsWithGroupStates(getApplication(), parsedCards, groupNameMap)
                        }
                        hasFullState -> {
                            // V1 full import with state and groups (decode base64 images)
                            val cards = parsedCards.map { CardImportExport.parsedCardToCard(getApplication(), it) }
                            val groupNamesPerCard = parsedCards.map { it.groupNames }
                            repository.importFullCards(cards, groupNamesPerCard, groupNameMap)
                        }
                        else -> {
                            // Simple import
                            val cardsToImport = parsedCards.map { it.question to it.answer }
                            repository.importCards(cardsToImport, algorithm)
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
}
