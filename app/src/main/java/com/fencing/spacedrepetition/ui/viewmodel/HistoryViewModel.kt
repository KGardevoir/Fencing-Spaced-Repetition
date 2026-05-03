package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GROUP_NAME_CARD_EDIT
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

    val searchQuery = MutableStateFlow("")
    val selectedGroup = MutableStateFlow<String?>(null)

    private val allLogsWithCards: Flow<List<ReviewLogWithCard>> =
        repository.getAllReviewLogs().transform { logs ->
            emit(logs.map { log ->
                val card = repository.getCardById(log.cardId)
                ReviewLogWithCard(log, card?.question ?: "Deleted Card")
            })
        }

    val availableGroups: StateFlow<List<String>> = allLogsWithCards
        .map { logs ->
            logs.mapNotNull { lwc ->
                lwc.reviewLog.groupName?.takeIf { it.isNotBlank() && it != GROUP_NAME_CARD_EDIT }
            }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Sessions and quick-grade logs merged in descending chronological order, with all active filters applied. */
    val historyItems: StateFlow<List<HistoryItem>> =
        combine(
            combine(
                repository.getCompletedSessions(),
                allLogsWithCards
            ) { sessions, logs -> sessions to logs },
            combine(searchQuery, selectedGroup, _opponentFilter) { q, g, o -> Triple(q, g, o) }
        ) { (sessions, allLogs), (query, group, opponentFilter) ->
            val logsBySession = allLogs
                .filter { it.reviewLog.sessionId != null }
                .groupBy { it.reviewLog.sessionId!! }
            val quickGradeLogs = allLogs.filter { it.reviewLog.sessionId == null }

            val items = mutableListOf<HistoryItem>()

            sessions.forEach { session ->
                val sessionLogs = logsBySession[session.id] ?: emptyList()
                val matchesGroup = group == null || sessionLogs.any { it.reviewLog.groupName == group }
                val matchesText = query.isBlank() || sessionLogs.any {
                    it.cardQuestion.contains(query, ignoreCase = true)
                }
                val matchesOpponent = opponentFilter == null || sessionLogs.any {
                    matchesOpponentFilter(it.reviewLog.opponentId, opponentFilter)
                }
                if (matchesGroup && matchesText && matchesOpponent) {
                    items.add(HistoryItem.Session(session))
                }
            }

            quickGradeLogs.forEach { lwc ->
                val matchesGroup = group == null || lwc.reviewLog.groupName == group
                val matchesText = query.isBlank() || lwc.cardQuestion.contains(query, ignoreCase = true)
                val matchesOpponent = opponentFilter == null ||
                    matchesOpponentFilter(lwc.reviewLog.opponentId, opponentFilter)
                if (matchesGroup && matchesText && matchesOpponent) {
                    items.add(HistoryItem.QuickGrade(lwc))
                }
            }

            items.sortedByDescending { item ->
                when (item) {
                    is HistoryItem.Session -> item.session.startTime
                    is HistoryItem.QuickGrade -> item.log.reviewLog.reviewTime
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun matchesOpponentFilter(logOpponentId: Long?, filter: Long): Boolean =
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
