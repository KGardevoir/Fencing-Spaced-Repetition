// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.algorithm.RetentionPlanner
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.ui.BinaryExportFile
import com.fencing.spacedrepetition.ui.ExportFile
import com.fencing.spacedrepetition.ui.ImportFile
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.CardWithGroupStates
import com.fencing.spacedrepetition.util.ImageStore
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.exportImageKeys
import com.fencing.spacedrepetition.util.photoArchiveEntries
import com.fencing.spacedrepetition.util.zipArchive
import com.fencing.spacedrepetition.util.Time
import com.fencing.spacedrepetition.util.parsedCardToCard
import com.fencing.spacedrepetition.util.parsedReviewLogsToEntities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Everything about cards, including moving them in and out as files.
 *
 * Import and export used to be half of a subclass in :app, because their
 * signatures named a Uri and a ContentResolver. They do not any more: a
 * chosen file arrives as an [ImportFile] or an [ExportFile], which is as much
 * as this needs to know about a file chooser, so the whole of the work --
 * parsing, group creation, formatting, the review-history section -- is
 * shared, and the browser runs the same code the phone does.
 *
 * [imageStore] is the other half of that. An import decodes inline base64
 * images and stores them; an export reads them back. Both are asynchronous in
 * a browser and neither is a file path any more.
 */
class CardViewModel(
    private val repository: CardRepository,
    private val groupRepository: GroupRepository,
    private val opponentRepository: OpponentRepository,
    private val imageStore: ImageStore
) : ViewModel() {

    val allCards: StateFlow<List<Card>> = repository.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCardsWithGroups: StateFlow<List<CardWithGroups>> = repository.getAllCardsWithGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueCardCount: StateFlow<Int> = repository.getDueCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cardCount: StateFlow<Int> = repository.getCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // How many days of review history the schedule estimate is fitted over
    private val _historyWindowDays = MutableStateFlow(RetentionPlanner.DEFAULT_HISTORY_WINDOW_DAYS)
    val historyWindowDays: StateFlow<Int> = _historyWindowDays.asStateFlow()

    fun setHistoryWindowDays(days: Int) {
        _historyWindowDays.value = days.coerceIn(
            RetentionPlanner.MIN_HISTORY_WINDOW_DAYS,
            RetentionPlanner.MAX_HISTORY_WINDOW_DAYS
        )
    }

    // Practice cadence sampled from recent review history, for retention suggestions
    @OptIn(ExperimentalCoroutinesApi::class)
    val practiceScheduleEstimate: StateFlow<ScheduleEstimate?> = _historyWindowDays
        .flatMapLatest { windowDays ->
            val now = Time.now()
            val windowStart = now - windowDays * 24L * 60 * 60 * 1000
            repository.getPracticeHistoryStats(windowStart)
                .map { RetentionPlanner.estimateSchedule(it, now, windowStart) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    // flatMapLatest is still experimental, and this is the second place in the
    // class that uses it -- the other one is annotated a few dozen lines up.
    @OptIn(ExperimentalCoroutinesApi::class)
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
                CardSortOption.REVIEWS -> filtered.sortedBy { it.fsrsReps }
                CardSortOption.DIFFICULTY -> filtered.sortedBy { it.fsrsDifficulty }
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
            repository.updateCard(card.copy(isDisabled = !card.isDisabled, modified = Time.now()))
        }
    }

    fun setSelectedCardsDisabled(disabled: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToUpdate = _selectedCardIds.value.toList()
            idsToUpdate.forEach { cardId ->
                val card = repository.getCardById(cardId) ?: return@forEach
                repository.updateCard(card.copy(isDisabled = disabled, modified = Time.now()))
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
        imagePaths: List<String> = emptyList(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val card = Card(
                    question = question,
                    answer = answer,
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
                repository.updateCard(card.copy(modified = Time.now()))
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

    // Import and export state, which the card list and group list screens
    // both render: a spinner, a dialog, or a message saying what happened.
    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    // ========== Archive (YAML) import and export ==========

    /**
     * Imports a chosen archive: cards, their groups, those groups' settings,
     * and the review history and opponents if the file carries them.
     */
    fun importCards(file: ImportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val contents = readArchive(file)
                if (contents is ArchiveContents.Failed) {
                    _importExportState.value = ImportExportState.Error(contents.message)
                    return@launch
                }
                val (parsedCards, parseErrors) = contents as ArchiveContents.Cards

                val groupIds = groupRepository.groupsForImport(parsedCards)

                val importedCount = when {
                    // V2/V3, where a card can hold one learning state per group.
                    parsedCards.any { it.isGroupSpecificState } ->
                        repository.importCardsWithGroupStates(parsedCards, groupIds) {
                            CardImportExport.parsedCardToCard(it, imageStore)
                        }
                    // V1, which has full state but only one of it per card.
                    parsedCards.any { it.hasFullState } ->
                        repository.importFullCards(
                            parsedCards.map { CardImportExport.parsedCardToCard(it, imageStore) },
                            parsedCards.map { it.groupNames },
                            groupIds
                        )
                    // Question and answer, and nothing else.
                    else -> repository.importCards(
                        parsedCards.map { it.concept to it.answer }
                    )
                }

                importOpponentsAndHistory()

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

    /**
     * Restores the opponents and the review logs an archive carried.
     *
     * After the cards, and only after them: a log is linked to its card by
     * question text, so the cards have to be in the database to be found.
     * Opponents already known by name keep their local skill multiplier --
     * someone else's opinion of an opponent does not overwrite yours.
     */
    private suspend fun importOpponentsAndHistory() {
        val parsedOpponents = CardImportExport.lastParsedOpponents
        val opponentIds: Map<String, Long> = if (parsedOpponents.isEmpty()) emptyMap() else {
            opponentRepository.ensureOpponentsExist(
                parsedOpponents.map { Triple(it.name, it.skillMultiplier, it.notes) }
            )
        }

        val parsedHistory = CardImportExport.lastParsedReviewHistory
        if (parsedHistory.isEmpty()) return

        val questionToCardId = repository.getAllCardsSync().associate { it.question to it.id }
        val reviewLogs = CardImportExport.parsedReviewLogsToEntities(
            parsedHistory, questionToCardId, opponentIds, imageStore
        )
        if (reviewLogs.isNotEmpty()) repository.importReviewLogs(reviewLogs)
    }

    /** Exports every card, optionally with the review history and opponents. */
    fun exportAllCards(file: ExportFile, includeHistory: Boolean = false) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            _importExportState.value = exportEverything(file, includeHistory)
        }
    }

    /**
     * Writes a backup -- everything, history included -- and returns whether
     * it worked.
     *
     * Separate from [exportAllCards] because of where it is called from: the
     * settings screen's "Back Up Now" and the home screen's reminder, neither
     * of which renders importExportState. A success left sitting in that
     * state would surface later as a dialog on the card list, over a file the
     * user has already been handed. A failure is worth surfacing that way; a
     * success is not.
     *
     * Suspending rather than launching, so the caller can record the backup
     * only if there was one -- see BackupScheduling.runNow.
     */
    suspend fun backUp(file: ExportFile): Boolean {
        val result = exportEverything(file, includeHistory = true)
        if (result is ImportExportState.Error) {
            _importExportState.value = result
            return false
        }
        return true
    }

    /** Gathers the whole collection and writes it, reporting what happened. */
    private suspend fun exportEverything(
        file: ExportFile,
        includeHistory: Boolean
    ): ImportExportState = try {
        val cardsWithStates = repository.getAllCardsWithGroupStates()
        if (cardsWithStates.isEmpty()) {
            ImportExportState.Error("No cards to export")
        } else {
            val exportedGroupNames = cardsWithStates.flatMap { it.groupNames }.toSet()
            val groups = groupRepository.getAllGroupsSync()
                .filter { it.name in exportedGroupNames }

            val reviewLogs =
                if (includeHistory) repository.getAllReviewLogsSync() else emptyList()
            // Every opponent, because every log is included -- the narrowing
            // the group export does has nothing to narrow to.
            val opponents =
                if (includeHistory) opponentRepository.getAllOpponentsSync() else emptyList()

            writeArchive(file, cardsWithStates, groups, reviewLogs, opponents)
        }
    } catch (e: Exception) {
        ImportExportState.Error("Export failed: ${e.message}")
    }

    /** Exports the cards of the chosen groups, and nothing else. */
    fun exportSelectedGroups(
        selectedGroupIds: List<Long>,
        file: ExportFile,
        includeHistory: Boolean = false
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val groups = groupRepository.getAllGroupsSync()
                    .filter { it.id in selectedGroupIds }
                if (groups.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No groups selected")
                    return@launch
                }

                val groupNames = groups.map { it.name }.toSet()
                val cardsWithStates = repository.getAllCardsWithGroupStates()
                    .filter { cardWithState -> cardWithState.groupNames.any { it in groupNames } }
                if (cardsWithStates.isEmpty()) {
                    _importExportState.value =
                        ImportExportState.Error("No cards found in selected groups")
                    return@launch
                }

                val exportedCardIds = cardsWithStates.map { it.card.id }.toSet()
                val reviewLogs = if (includeHistory) {
                    repository.getAllReviewLogsSync().filter { it.cardId in exportedCardIds }
                } else emptyList()

                // Only the opponents those logs name, so that exporting one
                // group does not hand over the whole roster.
                val referencedOpponentIds = reviewLogs.mapNotNull { it.opponentId }.toSet()
                val opponents: List<Opponent> = if (referencedOpponentIds.isEmpty()) emptyList() else {
                    opponentRepository.getAllOpponentsSync()
                        .filter { it.id in referencedOpponentIds }
                }

                _importExportState.value =
                    writeArchive(file, cardsWithStates, groups, reviewLogs, opponents)
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    /**
     * The write itself, which the two archive exports differ only in what
     * they hand it.
     *
     * The images are read through a reader built for exactly these rows --
     * see [ImageStore.readerFor] -- because the formatting is synchronous and
     * a browser's storage is not.
     */
    private suspend fun writeArchive(
        file: ExportFile,
        cardsWithStates: List<CardWithGroupStates>,
        groups: List<Group>,
        reviewLogs: List<ReviewLog>,
        opponents: List<Opponent>
    ): ImportExportState {
        val cardQuestions: Map<Long, String> = if (reviewLogs.isEmpty()) emptyMap() else {
            cardsWithStates.associate { it.card.id to it.card.question }
        }
        val images = imageStore.exportReader(cardsWithStates.map { it.card }, reviewLogs)

        return file.write { out ->
            CardImportExport.exportCardsWithGroupStates(
                cardsWithStates = cardsWithStates,
                out = out,
                images = images,
                groupSettings = groups,
                reviewLogs = reviewLogs,
                cardQuestions = cardQuestions,
                opponents = opponents,
                opponentNamesById = opponents.associate { it.id to it.name }
            )
        }.asImportExportState()
    }

    // ========== Photo export ==========

    /**
     * Writes every card and review photo to [file] as one zip archive.
     *
     * Photos already travel inside a deck export, inlined as base64, but only
     * in a file this app is the only reader of. This is the same pictures as
     * pictures -- the export to reach for when the photos are what is wanted,
     * rather than the deck they are attached to.
     *
     * Read through the same reader an archive export uses, so a browser's
     * store is awaited once, up front, rather than inside the packing.
     */
    fun exportAllPhotos(file: BinaryExportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cards = repository.getAllCardsSync()
                val reviewLogs = repository.getAllReviewLogsSync()

                if (exportImageKeys(cards, reviewLogs).isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No photos to export")
                    return@launch
                }

                val entries = photoArchiveEntries(
                    cards,
                    reviewLogs,
                    imageStore.exportReader(cards, reviewLogs)
                )

                // Keys with nothing behind them: an import that dropped an
                // image, or storage a browser evicted. The cards still show a
                // broken-image icon for these, so it is not a silent state.
                if (entries.isEmpty()) {
                    _importExportState.value =
                        ImportExportState.Error("No photos could be read for export")
                    return@launch
                }

                val failure = file.write(zipArchive(entries))
                _importExportState.value = if (failure == null) {
                    ImportExportState.PhotoExportSuccess(entries.size)
                } else {
                    ImportExportState.Error("Failed to save file: $failure")
                }
            } catch (e: Exception) {
                _importExportState.value =
                    ImportExportState.Error("Photo export failed: ${e.message}")
            }
        }
    }

    // ========== CSV import and export ==========

    /**
     * Step one of a CSV import: parse the file and ask where the cards go.
     *
     * Two steps because a CSV has no groups in it. The parsed cards are held
     * in the state until the user answers, so the file is read once.
     */
    fun csvImportParseFile(file: ImportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            _importExportState.value = try {
                readCsvForGroupSelection(file)
            } catch (e: Exception) {
                ImportExportState.Error("CSV import failed: ${e.message}")
            }
        }
    }

    /** Step two: the user has chosen a group, so write the cards into it. */
    fun csvImportComplete(
        parsedCards: List<ParsedCard>,
        parseErrors: List<String>,
        groupId: Long
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val importedCount =
                    importCsvCards(parsedCards, groupId, repository, groupRepository, imageStore)
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

    /** Exports every card as CSV: question, answer, and any images. */
    fun exportAllCardsCsv(file: ExportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithGroups = repository.getAllCardsWithGroupNames()
                    .map { (card, groupNames) -> CardWithGroupNames(card, groupNames) }

                if (cardsWithGroups.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export")
                    return@launch
                }

                _importExportState.value = writeCsv(file, cardsWithGroups)
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV export failed: ${e.message}")
            }
        }
    }

    /** Exports the chosen groups' cards as CSV. */
    fun exportSelectedGroupsCsv(selectedGroupIds: List<Long>, file: ExportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val groupNames = groupRepository.getAllGroupsSync()
                    .filter { it.id in selectedGroupIds }
                    .map { it.name }
                    .toSet()
                if (groupNames.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No groups selected")
                    return@launch
                }

                val cardsWithGroups = repository.getAllCardsWithGroupNames()
                    .map { (card, names) -> CardWithGroupNames(card, names) }
                    .filter { cardWithGroups -> cardWithGroups.groupNames.any { it in groupNames } }

                if (cardsWithGroups.isEmpty()) {
                    _importExportState.value =
                        ImportExportState.Error("No cards found in selected groups")
                    return@launch
                }

                _importExportState.value = writeCsv(file, cardsWithGroups)
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV export failed: ${e.message}")
            }
        }
    }

    private suspend fun writeCsv(
        file: ExportFile,
        cardsWithGroups: List<CardWithGroupNames>
    ): ImportExportState {
        val images = imageStore.exportReader(cardsWithGroups.map { it.card })
        return file.write { out ->
            CardImportExport.exportCardsToCsv(cardsWithGroups, out, images)
        }.asImportExportState()
    }
}
