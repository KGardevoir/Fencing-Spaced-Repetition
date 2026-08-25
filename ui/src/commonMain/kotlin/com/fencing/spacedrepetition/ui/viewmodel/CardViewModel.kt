// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
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
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything about cards that does not involve a file.
 *
 * open, and its repositories protected, because import and export are split
 * across the module boundary: their signatures are Uri and ContentResolver,
 * which exist only on Android, so that half subclasses this in :app. What is
 * here -- the list, the filters, sorting, selection, grading, learning state
 * -- needs nothing from a platform and now runs in a browser unchanged.
 */
open class CardViewModel(
    protected val repository: CardRepository,
    protected val groupRepository: GroupRepository,
    protected val opponentRepository: OpponentRepository
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

    // Import/Export state.
    //
    // protected, not private: the file-bound half of import and export lives
    // in :app, because a Uri and a ContentResolver do, and that half reports
    // its progress through this.
    protected val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    /**
     * Shows a message where import and export progress is shown.
     *
     * For a platform that cannot do a transfer at all: the browser reports
     * that here rather than leaving a button that appears to do nothing.
     */
    fun reportImportExportError(message: String) {
        _importExportState.value = ImportExportState.Error(message)
    }
}
