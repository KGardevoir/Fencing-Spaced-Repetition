package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
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

class HistoryViewModel(
    private val repository: CardRepository
) : ViewModel() {

    /** Sessions and quick-grade logs merged in descending chronological order. */
    val historyItems: StateFlow<List<HistoryItem>> =
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
