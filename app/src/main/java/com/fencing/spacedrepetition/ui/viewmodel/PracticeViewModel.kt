package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PracticeViewModel(private val repository: CardRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val _sessionCards = MutableStateFlow<List<SessionCard>>(emptyList())
    val sessionCards: StateFlow<List<SessionCard>> = _sessionCards.asStateFlow()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    /** Review logs created during this session, available after grading for adding notes. */
    private val _sessionReviewLogs = MutableStateFlow<List<ReviewLog>>(emptyList())
    val sessionReviewLogs: StateFlow<List<ReviewLog>> = _sessionReviewLogs.asStateFlow()

    private var sessionId: Long? = null
    private var selectedGroupId: Long? = null
    private var sessionStartTime: Long = 0L

    fun startNewSession(numberOfCards: Int = 3, groupId: Long? = null) {
        selectedGroupId = groupId
        sessionStartTime = System.currentTimeMillis()
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Loading

            try {
                // Get due cards (randomized from the full pool if setting is enabled)
                val dueCards = if (groupId != null) {
                    repository.getDueCardsByGroup(groupId, limit = numberOfCards)
                } else {
                    repository.getDueCards(limit = numberOfCards)
                }

                // If we have enough due cards, use those
                // Otherwise, get all cards to allow additional studying
                val cardsForSession = if (dueCards.size >= numberOfCards) {
                    dueCards.take(numberOfCards)
                } else {
                    val allCards = if (groupId != null) {
                        repository.getCardsByGroupSync(groupId)
                    } else {
                        repository.getAllCardsSync()
                    }
                    allCards.take(numberOfCards)
                }

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

    fun submitGrades() {
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Submitting

            try {
                val cards = _sessionCards.value

                // Validate all cards have grades
                if (cards.any { it.grade == null }) {
                    _uiState.value = PracticeUiState.Error("Please grade all cards")
                    return@launch
                }

                val cardsWithGrades = cards.mapNotNull { sessionCard ->
                    sessionCard.grade?.let { grade -> Pair(sessionCard.card, grade) }
                }

                // Only call review methods if there are cards to review
                if (cardsWithGrades.isNotEmpty()) {
                    // If practicing within a group, use group-aware review method
                    if (selectedGroupId != null) {
                        cardsWithGrades.forEach { (card, grade) ->
                            repository.reviewCardWithGroup(card, grade, selectedGroupId!!, sessionId)
                        }
                    } else {
                        repository.reviewMultipleCards(cardsWithGrades, sessionId)
                    }
                }

                // Complete session
                sessionId?.let { id ->
                    repository.completeSession(id, cards.mapNotNull { it.grade })
                }

                // Fetch the review logs created for this session so the user can add notes
                val sid = sessionId
                if (sid != null) {
                    val logs = repository.getReviewLogsBySession(sid).first()
                    _sessionReviewLogs.value = logs
                }

                _uiState.value = PracticeUiState.AddingNotes(sessionStartTime)
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error(e.message ?: "Failed to submit grades")
            }
        }
    }

    fun updateReviewLogNotes(reviewLogId: Long, notes: String, imagePaths: List<String>) {
        viewModelScope.launch {
            val logs = _sessionReviewLogs.value.toMutableList()
            val index = logs.indexOfFirst { it.id == reviewLogId }
            if (index >= 0) {
                val updated = logs[index].copy(
                    notes = notes,
                    imagePaths = imagePaths.joinToString(",")
                )
                repository.updateReviewLog(updated)
                logs[index] = updated
                _sessionReviewLogs.value = logs
            }
        }
    }

    fun resetSession() {
        _sessionCards.value = emptyList()
        _sessionReviewLogs.value = emptyList()
        _currentCardIndex.value = 0
        sessionId = null
        selectedGroupId = null
        sessionStartTime = 0L
        _uiState.value = PracticeUiState.Loading
    }
}

sealed class PracticeUiState {
    object Loading : PracticeUiState()
    object Practicing : PracticeUiState()
    object ReadyToGrade : PracticeUiState()
    object Submitting : PracticeUiState()
    /** Grades submitted; user can optionally add notes/images to review logs. */
    data class AddingNotes(val practiceStartTime: Long) : PracticeUiState()
    object Completed : PracticeUiState()
    object NoCards : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}
