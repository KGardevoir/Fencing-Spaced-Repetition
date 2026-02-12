package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PracticeViewModel(private val repository: CardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val _sessionCards = MutableStateFlow<List<SessionCard>>(emptyList())
    val sessionCards: StateFlow<List<SessionCard>> = _sessionCards.asStateFlow()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    private var sessionId: Long? = null
    private var selectedGroupId: Long? = null
    private var selectedGroupIds: List<Long> = emptyList()
    // Maps card ID to the group it should be reviewed in
    private var cardGroupAssociations: Map<Long, Long> = emptyMap()

    fun startNewSession(numberOfCards: Int = 3, groupId: Long? = null) {
        startNewSession(numberOfCards, if (groupId != null) listOf(groupId) else emptyList())
    }

    fun startNewSession(numberOfCards: Int = 3, groupIds: List<Long> = emptyList()) {
        selectedGroupIds = groupIds
        selectedGroupId = groupIds.firstOrNull()
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Loading

            try {
                val cardsForSession: List<Card>
                val associations = mutableMapOf<Long, Long>()

                if (groupIds.isEmpty()) {
                    // No group filter - practice all cards
                    val dueCards = repository.getDueCards(limit = numberOfCards)
                    cardsForSession = if (dueCards.size >= numberOfCards) {
                        dueCards.take(numberOfCards)
                    } else {
                        repository.getAllCardsSync().take(numberOfCards)
                    }
                } else if (groupIds.size == 1) {
                    // Single group - use existing behavior
                    val groupId = groupIds.first()
                    val dueCards = repository.getDueCardsByGroup(groupId, limit = numberOfCards)
                    val cards = if (dueCards.size >= numberOfCards) {
                        dueCards.take(numberOfCards)
                    } else {
                        repository.getCardsByGroupSync(groupId).take(numberOfCards)
                    }
                    cards.forEach { associations[it.id] = groupId }
                    cardsForSession = cards
                } else {
                    // Multiple groups - merge and deduplicate
                    val dueCardsWithGroups = repository.getDueCardsByGroups(groupIds, limit = numberOfCards)
                    if (dueCardsWithGroups.size >= numberOfCards) {
                        val taken = dueCardsWithGroups.take(numberOfCards)
                        taken.forEach { (card, gId) -> associations[card.id] = gId }
                        cardsForSession = taken.map { it.first }
                    } else {
                        val allCardsWithGroups = repository.getCardsByGroupsSync(groupIds)
                        val taken = allCardsWithGroups.take(numberOfCards)
                        taken.forEach { (card, gId) -> associations[card.id] = gId }
                        cardsForSession = taken.map { it.first }
                    }
                }

                cardGroupAssociations = associations

                if (cardsForSession.isEmpty()) {
                    _uiState.value = PracticeUiState.NoCards
                    return@launch
                }

                // Create session
                sessionId = repository.createPracticeSession(cardsForSession.map { it.id })

                // Initialize session cards
                _sessionCards.value = cardsForSession.map { SessionCard(it, null) }
                _currentCardIndex.value = 0
                _uiState.value = PracticeUiState.Practicing
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun nextCard() {
        val currentIndex = _currentCardIndex.value
        if (currentIndex < _sessionCards.value.size - 1) {
            _currentCardIndex.value = currentIndex + 1
        } else {
            // All cards viewed, move to grading
            _uiState.value = PracticeUiState.ReadyToGrade
        }
    }

    fun previousCard() {
        val currentIndex = _currentCardIndex.value
        if (currentIndex > 0) {
            _currentCardIndex.value = currentIndex - 1
        }
    }

    fun finishPractice() {
        _uiState.value = PracticeUiState.ReadyToGrade
    }

    fun backToPracticing() {
        _uiState.value = PracticeUiState.Practicing
    }

    fun updateGrade(cardIndex: Int, grade: Grade) {
        val cards = _sessionCards.value.toMutableList()
        if (cardIndex in cards.indices) {
            cards[cardIndex] = cards[cardIndex].copy(grade = grade)
            _sessionCards.value = cards
        }
    }

    fun updateCardText(cardIndex: Int, question: String, answer: String) {
        viewModelScope.launch {
            val cards = _sessionCards.value.toMutableList()
            if (cardIndex in cards.indices) {
                val oldCard = cards[cardIndex].card
                val updatedCard = oldCard.copy(
                    question = question,
                    answer = answer,
                    modified = System.currentTimeMillis()
                )
                repository.updateCard(updatedCard)
                cards[cardIndex] = cards[cardIndex].copy(card = updatedCard)
                _sessionCards.value = cards
            }
        }
    }

    fun updateCardImages(cardIndex: Int, imagePaths: List<String>) {
        viewModelScope.launch {
            val cards = _sessionCards.value.toMutableList()
            if (cardIndex in cards.indices) {
                val oldCard = cards[cardIndex].card
                val updatedCard = oldCard.copy(
                    imagePaths = imagePaths,
                    modified = System.currentTimeMillis()
                )
                repository.updateCard(updatedCard)
                cards[cardIndex] = cards[cardIndex].copy(card = updatedCard)
                _sessionCards.value = cards
            }
        }
    }

    fun updateCardComplete(cardIndex: Int, question: String, answer: String, imagePaths: List<String>) {
        viewModelScope.launch {
            val cards = _sessionCards.value.toMutableList()
            if (cardIndex in cards.indices) {
                val oldCard = cards[cardIndex].card
                val updatedCard = oldCard.copy(
                    question = question,
                    answer = answer,
                    imagePaths = imagePaths,
                    modified = System.currentTimeMillis()
                )
                repository.updateCard(updatedCard)
                cards[cardIndex] = cards[cardIndex].copy(card = updatedCard)
                _sessionCards.value = cards
            }
        }
    }

    fun submitGrades(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Submitting

            try {
                val cards = _sessionCards.value

                // Validate all cards have grades
                if (cards.any { it.grade == null }) {
                    _uiState.value = PracticeUiState.Error("Please grade all cards")
                    return@launch
                }

                // Review only non-skipped cards
                val cardsWithGrades = cards.mapNotNull { sessionCard ->
                    sessionCard.grade?.let { grade ->
                        // Skip cards marked with SKIP grade
                        if (grade != Grade.SKIP) {
                            Pair(sessionCard.card, grade)
                        } else {
                            null
                        }
                    }
                }

                // Only call review methods if there are cards to review
                if (cardsWithGrades.isNotEmpty()) {
                    if (selectedGroupIds.isNotEmpty()) {
                        // Use per-card group associations for group-aware review
                        cardsWithGrades.forEach { (card, grade) ->
                            val cardGroupId = cardGroupAssociations[card.id]
                            if (cardGroupId != null) {
                                repository.reviewCardWithGroup(card, grade, cardGroupId, sessionId)
                            } else {
                                repository.reviewCard(card, grade, sessionId)
                            }
                        }
                    } else {
                        repository.reviewMultipleCards(cardsWithGrades, sessionId)
                    }
                }

                // Complete session
                sessionId?.let { id ->
                    repository.completeSession(id, cards.mapNotNull { it.grade })
                }

                _uiState.value = PracticeUiState.Completed
                onComplete()
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error(e.message ?: "Failed to submit grades")
            }
        }
    }

    fun resetSession() {
        _sessionCards.value = emptyList()
        _currentCardIndex.value = 0
        sessionId = null
        selectedGroupId = null
        selectedGroupIds = emptyList()
        cardGroupAssociations = emptyMap()
        _uiState.value = PracticeUiState.Loading
    }
}

sealed class PracticeUiState {
    object Loading : PracticeUiState()
    object Practicing : PracticeUiState()
    object ReadyToGrade : PracticeUiState()
    object Submitting : PracticeUiState()
    object Completed : PracticeUiState()
    object NoCards : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}
