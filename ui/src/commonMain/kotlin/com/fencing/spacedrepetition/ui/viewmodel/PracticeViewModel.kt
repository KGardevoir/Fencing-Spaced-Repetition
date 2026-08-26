// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.model.SessionCard
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PracticeViewModel(
    private val repository: CardRepository,
    private val opponentRepository: OpponentRepository
) : ViewModel() {

    /** Available opponents for selection during grading. */
    val opponents: StateFlow<List<Opponent>> = opponentRepository.getAllOpponents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Create a new opponent from the grading screen; returns the new id (or -1 on conflict). */
    suspend fun createOpponent(name: String, skillMultiplier: Double): Long {
        return opponentRepository.insertOpponent(Opponent(name = name, skillMultiplier = skillMultiplier))
    }

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private val _sessionCards = MutableStateFlow<List<SessionCard>>(emptyList())
    val sessionCards: StateFlow<List<SessionCard>> = _sessionCards.asStateFlow()

    private val _currentCardIndex = MutableStateFlow(0)
    val currentCardIndex: StateFlow<Int> = _currentCardIndex.asStateFlow()

    /** The opponent selected for this session; all cards default to this. */
    private val _sessionOpponentId = MutableStateFlow<Long?>(null)
    val sessionOpponentId: StateFlow<Long?> = _sessionOpponentId.asStateFlow()

    /** Review logs created during this session, available after grading for adding notes. */
    private val _sessionReviewLogs = MutableStateFlow<List<ReviewLog>>(emptyList())
    val sessionReviewLogs: StateFlow<List<ReviewLog>> = _sessionReviewLogs.asStateFlow()

    private var sessionId: Long? = null
    private var selectedGroupId: Long? = null
    private var sessionStartTime: Long = 0L

    /**
     * Which session the view model is on, and the coroutine still setting it up.
     *
     * Starting a session is asynchronous -- due cards are read out of the
     * database and a session row is written -- while the tap that starts it
     * navigates to the practice screen straight away. Two taps on "Start
     * Practice", or a start still in flight when the user backs out and starts
     * again, therefore left two of those coroutines racing, and whichever
     * finished last overwrote [_sessionCards]. Due cards are drawn at random,
     * so the two lists are different sets of cards: the user could practise one
     * set and then be asked to grade another.
     *
     * Each start now takes a token. Cancelling the previous job stops most of
     * the losing work before it ever reaches the database, and the token check
     * covers the rest -- only the newest start may publish cards, a session id
     * or a state. [resetSession] moves the token on as well, so a start that
     * outlives a cancellation cannot repopulate a session the user has left.
     */
    private var sessionToken: Long = 0L
    private var sessionJob: Job? = null

    fun startNewSession(numberOfCards: Int = 3, groupId: Long? = null) {
        sessionJob?.cancel()
        val token = ++sessionToken

        // Cleared here rather than inside the coroutine so that the screen the
        // caller navigates to cannot draw the previous session first. On the
        // browser build viewModelScope does not dispatch immediately, so that
        // frame is real and not just theoretical.
        selectedGroupId = groupId
        sessionStartTime = Time.now()
        sessionId = null
        _sessionCards.value = emptyList()
        _sessionReviewLogs.value = emptyList()
        _currentCardIndex.value = 0
        _sessionOpponentId.value = null
        _uiState.value = PracticeUiState.Loading

        sessionJob = viewModelScope.launch {
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

                // Superseded while we were reading: say nothing and leave the
                // session that replaced this one alone.
                if (token != sessionToken) return@launch

                if (cardsForSession.isEmpty()) {
                    _uiState.value = PracticeUiState.NoCards
                    return@launch
                }

                // Create session
                val newSessionId = repository.createPracticeSession(cardsForSession.map { it.id })
                if (token != sessionToken) return@launch

                // Initialize session cards; no default opponent until user picks one
                sessionId = newSessionId
                _sessionOpponentId.value = null
                _sessionCards.value = cardsForSession.map { SessionCard(it, null) }
                _currentCardIndex.value = 0
                _uiState.value = PracticeUiState.Practicing
            } catch (e: CancellationException) {
                // A superseded start is not a failure the user should be shown.
                throw e
            } catch (e: Exception) {
                if (token == sessionToken) {
                    _uiState.value = PracticeUiState.Error(e.message ?: "Unknown error")
                }
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

    /**
     * Replace one card in the session list, atomically.
     *
     * [MutableStateFlow.update] rather than read-copy-write: grading, note
     * taking, the opponent pickers and the card editor all change the same
     * list, and the editor does it either side of a database write. A
     * read-copy-write there publishes a snapshot taken before that write
     * started, silently dropping any grade or note entered while it ran.
     */
    private fun mutateCard(cardIndex: Int, transform: (SessionCard) -> SessionCard) {
        _sessionCards.update { cards ->
            if (cardIndex !in cards.indices) return@update cards
            cards.toMutableList().also { it[cardIndex] = transform(it[cardIndex]) }
        }
    }

    fun updateGrade(cardIndex: Int, grade: Grade) {
        mutateCard(cardIndex) { it.copy(grade = grade) }
    }

    fun updateNotes(cardIndex: Int, notes: String, imagePaths: List<String>) {
        mutateCard(cardIndex) { it.copy(notes = notes, noteImagePaths = imagePaths) }
    }

    /** Set the session-level opponent and apply it to every card in the session. */
    fun setSessionOpponent(opponentId: Long?) {
        _sessionOpponentId.value = opponentId
        _sessionCards.update { cards -> cards.map { it.copy(opponentId = opponentId) } }
    }

    /** Override the opponent for a single card without changing the session default. */
    fun updateOpponent(cardIndex: Int, opponentId: Long?) {
        mutateCard(cardIndex) { it.copy(opponentId = opponentId) }
    }

    /** Update an opponent's skill multiplier. The change is persisted to the database
     *  and will be snapshotted into ReviewLog.stabilityMultiplier at submission. */
    fun updateOpponentDifficulty(opponentId: Long, newMultiplier: Double) {
        viewModelScope.launch {
            val opponent = opponentRepository.getOpponentById(opponentId) ?: return@launch
            opponentRepository.updateOpponent(opponent.copy(skillMultiplier = newMultiplier))
        }
    }

    /** Apply an opponent to every card that doesn't yet have one selected. */
    fun applyDefaultOpponent(opponentId: Long?) {
        _sessionCards.update { cards ->
            cards.map { card ->
                if (card.opponentId == null) card.copy(opponentId = opponentId) else card
            }
        }
    }

    /**
     * Edit the card at [cardIndex] and write it back to the database.
     *
     * The card is read by index but written back by id. An index only means
     * anything against the list it was read from, so going through the id is
     * what stops a database write that lands late from being applied to a
     * different card than the one the user edited.
     */
    private fun editCard(cardIndex: Int, edit: (Card) -> Card) {
        val original = _sessionCards.value.getOrNull(cardIndex)?.card ?: return
        viewModelScope.launch {
            val updatedCard = edit(original).copy(modified = Time.now())
            repository.updateCard(updatedCard)
            _sessionCards.update { cards ->
                val index = cards.indexOfFirst { it.card.id == updatedCard.id }
                if (index < 0) return@update cards
                cards.toMutableList().also { it[index] = it[index].copy(card = updatedCard) }
            }
        }
    }

    fun updateCardText(cardIndex: Int, question: String, answer: String) {
        editCard(cardIndex) { it.copy(question = question, answer = answer) }
    }

    fun updateCardImages(cardIndex: Int, imagePaths: List<String>) {
        editCard(cardIndex) { it.copy(imagePaths = imagePaths) }
    }

    fun updateCardComplete(cardIndex: Int, question: String, answer: String, imagePaths: List<String>) {
        editCard(cardIndex) { it.copy(question = question, answer = answer, imagePaths = imagePaths) }
    }

    fun submitGrades() {
        // Checked and set synchronously: a second tap on the confirmation
        // dialog would otherwise start a second submission and reschedule
        // every card in the session twice.
        if (_uiState.value is PracticeUiState.Submitting) return

        // The list as the user confirmed it, not as it may be some suspensions
        // later. It is also what decides the grade order written against the
        // session's card ids.
        val cards = _sessionCards.value

        // Validate all cards have grades
        if (cards.any { it.grade == null }) {
            _uiState.value = PracticeUiState.Error("Please grade all cards")
            return
        }

        val token = sessionToken
        val groupId = selectedGroupId
        val sid = sessionId
        _uiState.value = PracticeUiState.Submitting

        viewModelScope.launch {
            try {
                val cardsWithGrades = cards.mapNotNull { sessionCard ->
                    sessionCard.grade?.let { grade -> Triple(sessionCard.card, grade, sessionCard.opponentId) }
                }

                // Only call review methods if there are cards to review
                if (cardsWithGrades.isNotEmpty()) {
                    // If practicing within a group, use group-aware review method
                    if (groupId != null) {
                        cardsWithGrades.forEach { (card, grade, opponentId) ->
                            repository.reviewCardWithGroup(card, grade, groupId, sid, opponentId)
                        }
                    } else {
                        repository.reviewMultipleCards(cardsWithGrades, sid)
                    }
                }

                // Complete session
                sid?.let { id ->
                    repository.completeSession(id, cards.mapNotNull { it.grade })
                }

                // Fetch the review logs created for this session so the user can add notes
                if (sid != null) {
                    val logs = repository.getReviewLogsBySession(sid).first()

                    // Apply any notes/images that were entered during grading
                    val updatedLogs = logs.map { log ->
                        val sessionCard = cards.find { it.card.id == log.cardId }
                        if (sessionCard != null && (sessionCard.notes.isNotBlank() || sessionCard.noteImagePaths.isNotEmpty())) {
                            val updated = log.copy(
                                notes = sessionCard.notes,
                                imagePaths = sessionCard.noteImagePaths.joinToString(",")
                            )
                            repository.updateReviewLog(updated)
                            updated
                        } else {
                            log
                        }
                    }
                    if (token != sessionToken) return@launch
                    _sessionReviewLogs.value = updatedLogs
                }

                // The reviews are written either way; only the state the user
                // ends up on is withheld from a session that has moved on.
                if (token != sessionToken) return@launch
                _uiState.value = PracticeUiState.Completed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == sessionToken) {
                    _uiState.value = PracticeUiState.Error(e.message ?: "Failed to submit grades")
                }
            }
        }
    }

    fun updateReviewLogNotes(reviewLogId: Long, notes: String, imagePaths: List<String>) {
        val current = _sessionReviewLogs.value.firstOrNull { it.id == reviewLogId } ?: return
        viewModelScope.launch {
            val updated = current.copy(
                notes = notes,
                imagePaths = imagePaths.joinToString(",")
            )
            repository.updateReviewLog(updated)
            _sessionReviewLogs.update { logs ->
                val index = logs.indexOfFirst { it.id == reviewLogId }
                if (index < 0) return@update logs
                logs.toMutableList().also { it[index] = updated }
            }
        }
    }

    fun resetSession() {
        sessionJob?.cancel()
        sessionJob = null
        sessionToken++
        _sessionCards.value = emptyList()
        _sessionReviewLogs.value = emptyList()
        _currentCardIndex.value = 0
        _sessionOpponentId.value = null
        sessionId = null
        selectedGroupId = null
        sessionStartTime = 0L
        _uiState.value = PracticeUiState.Loading
    }
}
