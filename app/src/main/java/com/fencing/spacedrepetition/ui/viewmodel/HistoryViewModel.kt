package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Card
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

class HistoryViewModel(
    private val repository: CardRepository
) : ViewModel() {

    val completedSessions: StateFlow<List<PracticeSession>> = repository.getCompletedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val standaloneReviewLogs: StateFlow<List<ReviewLogWithCard>> =
        repository.getReviewLogsWithoutSession().transform { logs ->
            val logsWithCards = logs.map { log ->
                val card = repository.getCardById(log.cardId)
                ReviewLogWithCard(log, card?.question ?: "Deleted Card")
            }
            emit(logsWithCards)
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
}
