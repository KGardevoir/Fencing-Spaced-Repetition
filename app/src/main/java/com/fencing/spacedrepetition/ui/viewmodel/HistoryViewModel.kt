package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReviewLogWithCard(
    val reviewLog: ReviewLog,
    val cardQuestion: String
)

data class SessionWithReviews(
    val session: PracticeSession,
    val reviewLogs: List<ReviewLogWithCard>
)

sealed class HistoryItem {
    /** A completed practice session (may be expanded to show per-card grades). */
    data class Session(val session: PracticeSession) : HistoryItem()

    /** A single grade applied from the Add/Edit card screen (no session). */
    data class QuickGrade(val log: ReviewLogWithCard) : HistoryItem()
}

/** Sentinel filter value: show only sessions/logs that are unassigned (opponentId == null). */
const val OPPONENT_FILTER_NONE = -1L

class HistoryViewModel(
    private val repository: CardRepository,
    private val opponentRepository: OpponentRepository
) : ViewModel() {

    val opponents: StateFlow<List<Opponent>> = opponentRepository.getAllOpponents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filter: null = all, OPPONENT_FILTER_NONE = only unassigned, any other id = that opponent. */
    private val _opponentFilter = MutableStateFlow<Long?>(null)
    val opponentFilter: StateFlow<Long?> = _opponentFilter.asStateFlow()

    fun setOpponentFilter(opponentId: Long?) {
        _opponentFilter.value = opponentId
    }

    /** Sessions and quick-grade logs merged in descending chronological order. */
    private val allHistoryItems: Flow<List<HistoryItem>> =
        combine(
            repository.getCompletedSessions(),
            repository.getReviewLogsWithoutSession().transform { logs ->
                emit(logs.map { log ->
                    val card = repository.getCardById(log.cardId)
                    ReviewLogWithCard(log, card?.question ?: "Deleted Card")
                })
            }
        ) { sessions, quickGrades ->
            val items = mutableListOf<HistoryItem>()
            sessions.mapTo(items) { HistoryItem.Session(it) }
            quickGrades.mapTo(items) { HistoryItem.QuickGrade(it) }
            items.sortedByDescending { item ->
                when (item) {
                    is HistoryItem.Session -> item.session.startTime
                    is HistoryItem.QuickGrade -> item.log.reviewLog.reviewTime
                }
            }
        }

    /** Full list unfiltered (filtering is applied by historyItems for display). */
    val historyItems: StateFlow<List<HistoryItem>> =
        combine(allHistoryItems, _opponentFilter) { items, filter ->
            if (filter == null) {
                items
            } else {
                items.mapNotNull { item ->
                    when (item) {
                        is HistoryItem.Session -> {
                            val logs = repository.getReviewLogsBySession(item.session.id).first()
                            val matches = logs.any { log -> matchesFilter(log.opponentId, filter) }
                            if (matches) item else null
                        }
                        is HistoryItem.QuickGrade -> {
                            if (matchesFilter(item.log.reviewLog.opponentId, filter)) item else null
                        }
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun matchesFilter(logOpponentId: Long?, filter: Long): Boolean =
        if (filter == OPPONENT_FILTER_NONE) logOpponentId == null else logOpponentId == filter

    fun getReviewLogsForSession(sessionId: Long): Flow<List<ReviewLogWithCard>> =
        repository.getReviewLogsBySession(sessionId).transform { logs ->
            val logsWithCards = logs.map { log ->
                val card = repository.getCardById(log.cardId)
                ReviewLogWithCard(log, card?.question ?: "Deleted Card")
            }
            emit(logsWithCards)
        }

    fun getReviewLogsForCard(cardId: Long): Flow<List<ReviewLog>> =
        repository.getReviewLogsByCard(cardId)

    fun updateReviewLogNotes(reviewLog: ReviewLog, notes: String, imagePaths: List<String>) {
        viewModelScope.launch {
            repository.updateReviewLog(
                reviewLog.copy(
                    notes = notes,
                    imagePaths = imagePaths.joinToString(",")
                )
            )
        }
    }

    /** Reassign the opponent on a historical review log. Metadata-only — does not recompute FSRS state. */
    fun updateReviewLogOpponent(reviewLog: ReviewLog, opponentId: Long?) {
        viewModelScope.launch {
            repository.updateReviewLog(reviewLog.copy(opponentId = opponentId))
        }
    }

    /** Create a new opponent inline from the history editor. Returns the new id, or -1 on conflict. */
    suspend fun createOpponent(name: String, skillMultiplier: Double): Long =
        opponentRepository.insertOpponent(Opponent(name = name, skillMultiplier = skillMultiplier))
}
