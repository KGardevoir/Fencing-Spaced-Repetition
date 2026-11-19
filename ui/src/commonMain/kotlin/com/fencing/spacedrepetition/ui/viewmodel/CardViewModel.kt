// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.algorithm.RetentionPlanner
import com.fencing.spacedrepetition.algorithm.ScheduleEstimate
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardFilter
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.FsrsState
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningContexts
import com.fencing.spacedrepetition.data.model.ConceptOffer
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.ConceptRepository
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
import com.fencing.spacedrepetition.util.parsedReviewLogsToEntities
import com.fencing.spacedrepetition.util.parsedSessionToSession
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
/** What the card editor loads for a card it edits; empty for a new one. */
data class EditedCard(
    val groups: List<Group> = emptyList(),
    val learningStates: List<CardLearningState> = emptyList(),
    val concepts: List<Concept> = emptyList(),
    /** How much of each concept the card contains, where that is not 1. */
    val conceptWeights: Map<Long, Double> = emptyMap()
)

/**
 * What the card list shows: the cards that pass its filters, in its order,
 * and the filters, sorting and selection themselves.
 */
data class CardListState(
    /** The cards shown: filtered and sorted. */
    val cards: List<Card> = emptyList(),
    /** Every card with its groups, filtered or not. */
    val cardsWithGroups: List<CardWithGroups> = emptyList(),
    val groups: List<Group> = emptyList(),
    val learningGroups: List<LearningGroup> = emptyList(),
    val concepts: List<Concept> = emptyList(),
    /** Each card's concepts with how much of each it contains, by card id. */
    val conceptsByCard: Map<Long, List<Pair<Concept, Double>>> = emptyMap(),
    val cardCount: Int = 0,
    val groupFilters: Set<Long> = emptySet(),
    val conceptFilters: Set<Long> = emptySet(),
    val showDisabled: Boolean = false,
    val searchQuery: String = "",
    val sortOption: CardSortOption = CardSortOption.DUE_DATE,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val selectionMode: Boolean = false,
    val selectedCardIds: Set<Long> = emptySet()
)

class CardViewModel(
    private val repository: CardRepository,
    private val groupRepository: GroupRepository,
    private val opponentRepository: OpponentRepository,
    private val imageStore: ImageStore,
    private val conceptRepository: ConceptRepository
) : ViewModel() {

    val allCards: StateFlow<List<Card>> = repository.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allCardsWithGroups: StateFlow<List<CardWithGroups>> = repository.getAllCardsWithGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueCardCount: StateFlow<Int> = repository.drawing.getDueCardCount()
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

    // Groups for filtering
    private val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupFilters = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedGroupFilters: StateFlow<Set<Long>> = _selectedGroupFilters.asStateFlow()

    /** Concepts to filter the card list by: a card carrying any of them is shown. */
    private val _selectedConceptFilters = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedConceptFilters: StateFlow<Set<Long>> = _selectedConceptFilters.asStateFlow()

    fun toggleConceptFilter(conceptId: Long) {
        _selectedConceptFilters.update { if (conceptId in it) it - conceptId else it + conceptId }
    }

    // A filter on a concept or group that has since been deleted has no chip
    // to turn it off by, and would go on filtering: drop it with the record.
    init {
        viewModelScope.launch {
            conceptRepository.getAllConcepts().collect { concepts ->
                val ids = concepts.map { it.id }.toSet()
                _selectedConceptFilters.update { it intersect ids }
            }
        }
        viewModelScope.launch {
            groupRepository.getAllGroups().collect { groups ->
                val ids = groups.map { it.id }.toSet()
                _selectedGroupFilters.update { it intersect ids }
            }
        }
    }

    private val _showDisabledFilter = MutableStateFlow(false)
    private val showDisabledFilter: StateFlow<Boolean> = _showDisabledFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _cardSortOption = MutableStateFlow(CardSortOption.DUE_DATE)
    private val cardSortOption: StateFlow<CardSortOption> = _cardSortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASCENDING)
    private val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

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
    private val filteredCards: StateFlow<List<Card>> = _showDisabledFilter.flatMapLatest { showDisabled ->
        combine(
            combine(allCardsWithGroups, conceptRepository.getAllCardConceptCrossRefs()) { cards, refs -> cards to refs },
            combine(_selectedGroupFilters, _selectedConceptFilters) { groups, concepts -> groups to concepts },
            searchQuery,
            _cardSortOption,
            _sortDirection
        ) { (cardsWithGroups, conceptRefs), (groupIds, conceptIds), query, sortOption, direction ->
            val filtered = CardFilter(groupIds, conceptIds, showDisabled, query).apply(cardsWithGroups, conceptRefs)

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

    fun toggleGroupFilter(groupId: Long) {
        _selectedGroupFilters.value = if (groupId in _selectedGroupFilters.value) {
            _selectedGroupFilters.value - groupId
        } else {
            _selectedGroupFilters.value + groupId
        }
    }

    fun clearGroupFilters() {
        _selectedGroupFilters.value = emptySet()
        _selectedConceptFilters.value = emptySet()
        _showDisabledFilter.value = false
    }

    fun toggleDisabledFilter() {
        _showDisabledFilter.value = !_showDisabledFilter.value
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

    fun getLearningStatesForCard(cardId: Long): Flow<List<CardLearningState>> =
        groupRepository.getAllLearningStatesForCard(cardId)

    fun saveLearningState(learningState: CardLearningState) {
        viewModelScope.launch { repository.saveLearningState(learningState) }
    }

    fun getDueCardCountByGroup(groupId: Long): Flow<Int> =
        repository.drawing.getDueCardCountByGroup(groupId)

    fun getCardCountByGroup(groupId: Long): Flow<Int> =
        repository.getCardCountByGroup(groupId)

    fun getConceptsForCard(cardId: Long): Flow<List<Concept>> = conceptRepository.getConceptsForCard(cardId)

    /** A card's concept tags, with how much of each concept it contains. */
    fun getConceptLinksForCard(cardId: Long): Flow<List<CardConceptCrossRef>> =
        conceptRepository.getConceptLinksForCard(cardId)

    val allConcepts: StateFlow<List<Concept>> = conceptRepository.getAllConcepts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Each card's concepts with how much of each it contains, by card id,
     * sorted by name -- for showing them on the card list.
     */
    private val conceptsByCard: StateFlow<Map<Long, List<Pair<Concept, Double>>>> = combine(
        conceptRepository.getAllConcepts(),
        conceptRepository.getAllCardConceptCrossRefs()
    ) { concepts, refs ->
        val byId = concepts.associateBy { it.id }
        refs.groupBy({ it.cardId }, { ref -> byId[ref.conceptId]?.let { it to ref.weight } })
            .mapValues { (_, list) -> list.filterNotNull().sortedBy { it.first.name.lowercase() } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** What the card editor offers a card: see [ConceptOffer]. */
    val conceptOffer: StateFlow<ConceptOffer> = combine(
        groupRepository.getAllGroups(),
        groupRepository.getAllLearningGroups(),
        conceptRepository.getAllConcepts(),
        conceptRepository.getAllConceptLearningGroups()
    ) { groups, learningGroups, concepts, links ->
        ConceptOffer(LearningContexts(groups, learningGroups), concepts, links)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConceptOffer.NONE)

    /** What the card editor loads for [cardId]: its groups, learning-group progress and concepts. */
    fun editedCard(cardId: Long): Flow<EditedCard> = combine(
        getGroupsForCard(cardId),
        getLearningStatesForCard(cardId),
        getConceptsForCard(cardId),
        getConceptLinksForCard(cardId)
    ) { groups, states, concepts, links ->
        EditedCard(groups, states, concepts, links.filter { it.weight != 1.0 }.associate { it.conceptId to it.weight })
    }

    /**
     * Create a concept from the card editor -- or find the one of that name --
     * available in [contexts], the card's learning contexts that schedule by
     * concept, so it can be picked for the card. [onCreated] gets its id.
     */
    fun createConcept(name: String, contexts: Set<Long?>, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onCreated(conceptRepository.getOrCreateConceptIn(name, contexts))
            } catch (e: Exception) {
                // A blank name: nothing to create
            }
        }
    }

    fun addCard(
        question: String,
        answer: String,
        groupIds: List<Long>,
        imagePaths: List<String> = emptyList(),
        concepts: Map<Long, Double> = emptyMap(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val card = Card(
                    question = question,
                    answer = answer,
                    imagePaths = imagePaths
                )
                val cardId = repository.insertCardWithGroups(card, groupIds)
                if (concepts.isNotEmpty()) repository.conceptDerivation.setCardConcepts(cardId, concepts.keys, concepts)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    /** [concepts]: each concept the card carries and how much of it; null leaves them as they are. */
    /** Saves [card]; [groupIds] and [concepts] are left as they are when null. */
    fun updateCard(card: Card, groupIds: List<Long>?, concepts: Map<Long, Double>?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateCard(card.copy(modified = Time.now()))
                groupIds?.let { repository.updateCardGroups(card.id, it) }
                concepts?.let { repository.conceptDerivation.setCardConcepts(card.id, it.keys, it) }
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Selection state for bulk operations
    private val _selectedCardIds = MutableStateFlow<Set<Long>>(emptySet())
    private val selectedCardIds: StateFlow<Set<Long>> = _selectedCardIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    private val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /** Everything the card list shows, as one value. */
    val cardListState: StateFlow<CardListState> = combine(
        combine(filteredCards, allCardsWithGroups, allGroups, groupRepository.getAllLearningGroups(), cardCount) {
                cards, withGroups, groups, learningGroups, count ->
            CardListState(cards, withGroups, groups, learningGroups, cardCount = count)
        },
        combine(allConcepts, conceptsByCard, _selectedGroupFilters, _selectedConceptFilters, _showDisabledFilter) {
                concepts, byCard, groupFilters, conceptFilters, showDisabled ->
            CardListState(
                concepts = concepts, conceptsByCard = byCard, groupFilters = groupFilters,
                conceptFilters = conceptFilters, showDisabled = showDisabled
            )
        },
        combine(_searchQuery, _cardSortOption, _sortDirection, _isSelectionMode, _selectedCardIds) {
                query, sortOption, direction, selecting, selected ->
            CardListState(
                searchQuery = query, sortOption = sortOption, sortDirection = direction,
                selectionMode = selecting, selectedCardIds = selected
            )
        }
    ) { content, filters, view ->
        content.copy(
            concepts = filters.concepts, conceptsByCard = filters.conceptsByCard, groupFilters = filters.groupFilters,
            conceptFilters = filters.conceptFilters, showDisabled = filters.showDisabled,
            searchQuery = view.searchQuery, sortOption = view.sortOption, sortDirection = view.sortDirection,
            selectionMode = view.selectionMode, selectedCardIds = view.selectedCardIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardListState())

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

    /** Selects every card shown -- or, when they are all selected already, none. */
    fun selectAllCards() {
        val shown = filteredCards.value.map { it.id }.toSet()
        _selectedCardIds.value =
            if (shown.isNotEmpty() && _selectedCardIds.value.containsAll(shown)) _selectedCardIds.value - shown
            else _selectedCardIds.value + shown
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

    /** Resets the selected cards' states in the given learning groups. */
    fun resetSelectedCardsInGroups(learningGroupIds: Set<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToReset = _selectedCardIds.value.toList()
            idsToReset.forEach { cardId ->
                learningGroupIds.forEach { learningGroupId ->
                    repository.resetCardStateInLearningGroup(cardId, learningGroupId)
                }
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    fun resetSelectedCardsBothStates(learningGroupIds: Set<Long>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val idsToReset = _selectedCardIds.value.toList()
            idsToReset.forEach { cardId ->
                // Reset global state
                repository.resetCardState(cardId, resetGroupStates = false)
                // Reset the chosen learning group states
                learningGroupIds.forEach { learningGroupId ->
                    repository.resetCardStateInLearningGroup(cardId, learningGroupId)
                }
            }
            _selectedCardIds.value = emptySet()
            _isSelectionMode.value = false
            onComplete()
        }
    }

    /** Resets [cardId]'s Global progress (and every learning group's, if asked); [onComplete] gets the card as reset. */
    fun resetCardState(cardId: Long, resetGroupStates: Boolean = false, onComplete: (Card) -> Unit = {}) {
        viewModelScope.launch {
            repository.resetCardState(cardId, resetGroupStates)?.let(onComplete)
        }
    }

    fun resetCardStateInGroup(cardId: Long, groupId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetCardStateInGroup(cardId, groupId)
            onComplete()
        }
    }

    /**
     * Logs a grade staged in the card editor, without changing any progress:
     * [before] and [after] are the progress where it was graded, in [groupId]'s
     * learning context (Global for none).
     */
    fun recordGradeFromEdit(cardId: Long, before: FsrsState, after: FsrsState, grade: Grade, groupId: Long?) {
        viewModelScope.launch {
            try {
                repository.grading.logGradeFromEdit(cardId, before, after, grade, groupId)
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
                    repository.grading.computeReviewWithGroup(card, grade, groupId)
                } else {
                    repository.grading.computeReview(card, grade)
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

                val importedCount = importArchiveCards(parsedCards, repository, groupRepository, conceptRepository, imageStore)
                importOpponentsAndHistory()
                // With the history in, concepts are derived from it -- except
                // those with none, which keep the state the file brought.
                repository.conceptDerivation.rederiveConcepts(onlyWithHistory = true)

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
     * Opponents already here take the file's name, notes and multiplier:
     * an import updates everything but progress.
     */
    private suspend fun importOpponentsAndHistory() {
        val parsedOpponents = CardImportExport.lastParsedOpponents
        val opponentIds: Map<String, Long> = if (parsedOpponents.isEmpty()) emptyMap() else {
            opponentRepository.ensureOpponentsExist(
                parsedOpponents.map { Triple(it.name, it.skillMultiplier, it.notes) },
                uidsByName = parsedOpponents.mapNotNull { o -> o.uid?.let { o.name to it } }.toMap()
            )
        }

        val parsedHistory = CardImportExport.lastParsedReviewHistory
        if (parsedHistory.isEmpty()) {
            // Opponents alone can still reconnect reviews to someone re-added.
            repository.reconcileOpponentReferences()
            return
        }

        val questionToCardId = repository.getAllCardsSync().associate { it.question to it.id }
        val cardIdByUid = repository.archive.cardIdsByUid()
        // A review's card: by stable identifier where the file has one.
        val cardIdOf = { log: CardImportExport.ParsedReviewLog ->
            log.cardUid?.let { cardIdByUid[it] } ?: questionToCardId[log.cardQuestion]
        }
        val sessionIds = restoreSessions(parsedHistory, cardIdOf)
        val reviewLogs = CardImportExport.parsedReviewLogsToEntities(
            parsedHistory, questionToCardId, opponentIds, imageStore,
            sessionIdByStart = sessionIds.byStart,
            cardIdByUid = cardIdByUid,
            sessionIdByUid = sessionIds.byUid
        )
        // Reviews already in the history are left out, so nothing counts twice.
        if (reviewLogs.isNotEmpty()) repository.archive.importReviewLogs(reviewLogs)
        // Each review's opponent: the identifier the file gave, pointed at
        // whoever has it here; a review the file named only by name gets the
        // identifier of the opponent that name found.
        repository.reconcileOpponentReferences()
    }

    /**
     * Recreates the sessions an archive's history belongs to, and returns each
     * one's local id by its start time -- which is how the history names them.
     *
     * Only sessions with at least one review this import can place: a session
     * holds the notes on a fight, and one none of whose cards are here would be
     * notes on nothing. Its cards and grades are rebuilt from those reviews, in
     * the order they were made, since the archive does not repeat them.
     */
    private suspend fun restoreSessions(
        parsedHistory: List<CardImportExport.ParsedReviewLog>,
        cardIdOf: (CardImportExport.ParsedReviewLog) -> Long?
    ): RestoredSessions {
        val parsedSessions = CardImportExport.lastParsedSessions
        if (parsedSessions.isEmpty()) return RestoredSessions(emptyMap(), emptyMap())

        val placeable = parsedHistory.filter { it.sessionStart != null && cardIdOf(it) != null }
        val byStart = mutableMapOf<Long, Long>()
        val byUid = mutableMapOf<String, Long>()
        parsedSessions.forEach { parsed ->
            // A review names its session by identifier, or by start time in older files.
            val reviews = placeable
                .filter { log -> log.sessionUid?.let { it == parsed.uid } ?: (log.sessionStart == parsed.startTime) }
                .sortedBy { it.reviewTime }
                .ifEmpty { return@forEach }
            val session = CardImportExport.parsedSessionToSession(
                parsed,
                cardIds = reviews.map { cardIdOf(it)!! },
                grades = reviews.map { it.grade },
                images = imageStore
            )
            val id = repository.archive.ensureSession(session)
            byStart[parsed.startTime] = id
            parsed.uid?.let { byUid[it] = id }
        }
        return RestoredSessions(byStart, byUid)
    }

    /** Local session ids an import placed, by start time and by stable identifier. */
    private data class RestoredSessions(val byStart: Map<Long, Long>, val byUid: Map<String, Long>)

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
        val cardsWithStates = repository.archive.getAllCardsWithGroupStates()
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
            // The sessions carry the notes on each fight; without them a
            // history export would lose every one.
            val sessions =
                if (includeHistory) repository.getAllSessionsSync() else emptyList()

            writeArchive(file, cardsWithStates, groups, reviewLogs, opponents, sessions)
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
                val cardsWithStates = repository.archive.getAllCardsWithGroupStates()
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

                // Only the sessions those logs were part of. A session's notes
                // are about the whole fight, so they come whole, even where
                // they mention a card outside the groups being exported.
                val referencedSessionIds = reviewLogs.mapNotNull { it.sessionId }.toSet()
                val sessions: List<PracticeSession> = if (referencedSessionIds.isEmpty()) emptyList() else {
                    repository.getAllSessionsSync().filter { it.id in referencedSessionIds }
                }

                _importExportState.value =
                    writeArchive(file, cardsWithStates, groups, reviewLogs, opponents, sessions)
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
        opponents: List<Opponent>,
        sessions: List<PracticeSession>
    ): ImportExportState {
        val cardQuestions: Map<Long, String> = if (reviewLogs.isEmpty()) emptyMap() else {
            cardsWithStates.associate { it.card.id to it.card.question }
        }
        val images = imageStore.exportReader(cardsWithStates.map { it.card }, reviewLogs, sessions)
        val learningGroups = groupRepository.getAllLearningGroupsSync()
        val concepts = repository.archive.getAllConceptsWithStates()

        return file.write { out ->
            CardImportExport.exportCardsWithGroupStates(
                cardsWithStates = cardsWithStates,
                out = out,
                images = images,
                groupSettings = groups,
                learningGroups = learningGroups,
                reviewLogs = reviewLogs,
                cardQuestions = cardQuestions,
                opponents = opponents,
                opponentNamesById = opponents.associate { it.id to it.name },
                sessions = sessions,
                concepts = concepts
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
                val sessions = repository.getAllSessionsSync()

                if (exportImageKeys(cards, reviewLogs, sessions).isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No photos to export")
                    return@launch
                }

                val entries = photoArchiveEntries(
                    cards,
                    reviewLogs,
                    imageStore.exportReader(cards, reviewLogs, sessions),
                    sessions
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
                val cardsWithGroups = repository.archive.getAllCardsWithGroupNames()
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

                val cardsWithGroups = repository.archive.getAllCardsWithGroupNames()
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
