package com.fencing.spacedrepetition.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.algorithm.RetentionPlanner
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ParsedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CardSortOption(val label: String) {
    DUE_DATE("Due Date"),
    NAME("Name"),
    REVIEWS("Reviews"),
    DIFFICULTY("Difficulty")
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}

class CardViewModel(
    application: Application,
    private val repository: CardRepository,
    private val groupRepository: GroupRepository,
    private val opponentRepository: OpponentRepository
) : AndroidViewModel(application) {

    val allCards: StateFlow<List<Card>> = repository.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCardsWithGroups: StateFlow<List<CardWithGroups>> = repository.getAllCardsWithGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueCardCount: StateFlow<Int> = repository.getDueCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cardCount: StateFlow<Int> = repository.getCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Practice cadence sampled from recent review history, for retention suggestions
    val practiceScheduleEstimate: StateFlow<ScheduleEstimate?> = run {
        val now = System.currentTimeMillis()
        val windowStart = now - RetentionPlanner.HISTORY_WINDOW_DAYS * 24L * 60 * 60 * 1000
        repository.getPracticeHistoryStats(windowStart)
            .map { RetentionPlanner.estimateSchedule(it, now, windowStart) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun getCardCountForGroup(groupId: Long): Flow<Int> = repository.getCardCountByGroup(groupId)

    // Groups for filtering
    val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupFilter = MutableStateFlow<Group?>(null)
    val selectedGroupFilter: StateFlow<Group?> = _selectedGroupFilter.asStateFlow()

    private val _selectedGroupFilters = MutableStateFlow<Set<Long>>(emptySet())
    val selectedGroupFilters: StateFlow<Set<Long>> = _selectedGroupFilters.asStateFlow()

    private val _showDisabledFilter = MutableStateFlow(false)
    val showDisabledFilter: StateFlow<Boolean> = _showDisabledFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _cardSortOption = MutableStateFlow(CardSortOption.DUE_DATE)
    val cardSortOption: StateFlow<CardSortOption> = _cardSortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASCENDING)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    fun setCardSortOption(option: CardSortOption) {
        _cardSortOption.value = option
    }

    fun toggleSortDirection() {
        _sortDirection.value = when (_sortDirection.value) {
            SortDirection.ASCENDING -> SortDirection.DESCENDING
            SortDirection.DESCENDING -> SortDirection.ASCENDING
        }
    }

    val filteredCards: StateFlow<List<Card>> = _showDisabledFilter.flatMapLatest { showDisabled ->
        combine(
            allCardsWithGroups,
            _selectedGroupFilters,
            searchQuery,
            _cardSortOption,
            _sortDirection
        ) { cardsWithGroups, groupIds, query, sortOption, direction ->
            var filtered = cardsWithGroups
                .filter { it.card.isDisabled == showDisabled }
                .filter { groupIds.isEmpty() || it.groups.any { g -> g.id in groupIds } }
                .map { it.card }

            // Apply search filter
            if (query.isNotBlank()) {
                val searchLower = query.lowercase()
                filtered = filtered.filter { card ->
                    card.question.lowercase().contains(searchLower) ||
                    card.answer.lowercase().contains(searchLower)
                }
            }

            // Apply sort with direction
            val sorted = when (sortOption) {
                CardSortOption.DUE_DATE -> filtered.sortedBy { it.nextReview }
                CardSortOption.NAME -> filtered.sortedBy { it.question.lowercase() }
                CardSortOption.REVIEWS -> filtered.sortedBy {
                    when (it.algorithm) {
                        AlgorithmType.FSRS -> it.fsrsReps
                        AlgorithmType.SM2 -> it.sm2Repetitions
                    }
                }
                CardSortOption.DIFFICULTY -> filtered.sortedBy {
                    when (it.algorithm) {
                        AlgorithmType.FSRS -> it.fsrsDifficulty
                        AlgorithmType.SM2 -> 2.5 - it.sm2EaseFactor
                    }
                }
            }

            if (direction == SortDirection.DESCENDING) sorted.reversed() else sorted
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGroupFilter(group: Group?) {
        _selectedGroupFilter.value = group
    }

    fun toggleGroupFilter(groupId: Long) {
        _selectedGroupFilters.value = if (groupId in _selectedGroupFilters.value) {
            _selectedGroupFilters.value - groupId
        } else {
            _selectedGroupFilters.value + groupId
        }
    }

    fun clearGroupFilters() {
        _selectedGroupFilters.value = emptySet()
        _showDisabledFilter.value = false
    }

    fun toggleDisabledFilter() {
        _showDisabledFilter.value = !_showDisabledFilter.value
    }

    fun toggleCardDisabled(cardId: Long) {
        viewModelScope.launch {
            val card = repository.getCardById(cardId) ?: return@launch
            repository.updateCard(card.copy(isDisabled = !card.isDisabled, modified = System.currentTimeMillis()))
        }
    }

    fun setSelectedCardsDisabled(disabled: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToUpdate = _selectedCardIds.value.toList()
            idsToUpdate.forEach { cardId ->
                val card = repository.getCardById(cardId) ?: return@forEach
                repository.updateCard(card.copy(isDisabled = disabled, modified = System.currentTimeMillis()))
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
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

    fun deleteAllCards(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllCards()
            onComplete()
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

    /** Records a review log for a grade applied from the Add/Edit card screen, without updating card state. */
    fun recordGradeFromEdit(cardBefore: Card, cardAfter: Card, grade: Grade, groupId: Long? = null) {
        viewModelScope.launch {
            try {
                repository.logGradeFromEdit(cardBefore, cardAfter, grade, groupId)
            } catch (e: Exception) {
                // Non-fatal: history logging should not break the save flow
            }
        }
    }

    /** Compute the result of grading a card without persisting. Used for staged Quick Grade. */
    fun computeGradeCard(cardId: Long, grade: Grade, groupId: Long? = null, onComplete: (Card) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val card = repository.getCardById(cardId) ?: return@launch
                val computed = if (groupId != null) {
                    repository.computeReviewWithGroup(card, grade, groupId)
                } else {
                    repository.computeReview(card, grade)
                }
                onComplete(computed)
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
                            opponents, opponentNamesById
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
                            opponents, opponentNamesById
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
                            getApplication(), parsedReviewHistory, questionToCardId, opponentNameToId
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
                            CardImportExport.decodeImageFromBase64(getApplication(), base64Data)
                        }

                        val existing = repository.findCardByQuestion(parsed.concept)
                        if (existing != null) {
                            val updated = existing.copy(
                                answer = parsed.answer,
                                imagePaths = if (decodedImagePaths.isNotEmpty()) decodedImagePaths else existing.imagePaths,
                                modified = System.currentTimeMillis()
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
                        CardImportExport.exportCardsToCsv(cardsWithGroups, fileStream)
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
                        CardImportExport.exportCardsToCsv(filteredCards, fileStream)
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
