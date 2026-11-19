// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import com.fencing.spacedrepetition.data.model.LearningContexts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.ConceptRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: CardRepository,
    private val opponentRepository: OpponentRepository,
    conceptRepository: ConceptRepository? = null
) : ViewModel() {

    /** Concept names by uid and id, and which concepts each card carries now. */
    private class ConceptIndex(
        val nameByUid: Map<String, String> = emptyMap(),
        val nameById: Map<Long, String> = emptyMap(),
        val conceptIdsByCard: Map<Long, List<Long>> = emptyMap()
    ) {
        /**
         * What [log] tested: the concepts it was drawn for (the trigger first),
         * then the card's other concepts as it carries them now. A concept
         * since deleted is left out.
         */
        fun testedBy(log: ReviewLog): List<TestedConcept> {
            val recorded = log.selectionConceptUids.mapNotNull { uid ->
                nameByUid[uid]?.let { TestedConcept(it, drawnFor = true, trigger = uid == log.selectionTrigger) }
            }.sortedWith(compareByDescending<TestedConcept> { it.trigger }.thenBy { it.name.lowercase() })
            val recordedNames = recorded.map { it.name }.toSet()
            val current = conceptIdsByCard[log.cardId].orEmpty()
                .mapNotNull { nameById[it] }
                .filter { it !in recordedNames }
                .sortedBy { it.lowercase() }
                .map { TestedConcept(it) }
            return recorded + current
        }
    }

    private val conceptIndex: Flow<ConceptIndex> = if (conceptRepository == null) flowOf(ConceptIndex()) else
        combine(conceptRepository.getAllConcepts(), conceptRepository.getAllCardConceptCrossRefs()) { concepts, links ->
            ConceptIndex(
                nameByUid = concepts.associate { it.uid to it.name },
                nameById = concepts.associate { it.id to it.name },
                conceptIdsByCard = links.groupBy({ it.cardId }, { it.conceptId })
            )
        }

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

    /**
     * Every log with its card's question, and with the group it was made in
     * named as that group is called now -- found by its stable identifier, so
     * a renamed group's history shows, and filters, under the new name. A log
     * from before identifiers, or of a group since deleted, keeps the name it
     * noted.
     */
    private val allLogsWithCards: Flow<List<ReviewLogWithCard>> =
        combine(repository.getAllReviewLogs(), repository.getAllGroups(), conceptIndex) { logs, groups, index ->
            Triple(logs, groups, index)
        }
            .transform { (logs, groups, index) ->
                val currentNames = groups.associate { it.uid to it.name }
                emit(logs.map { log ->
                    val card = repository.getCardById(log.cardId)
                    val named = log.groupUid?.let { currentNames[it] }?.let { log.copy(groupName = it) } ?: log
                    ReviewLogWithCard(named, card?.question ?: "Deleted Card", index.testedBy(log))
                })
            }

    val availableGroups: StateFlow<List<String>> = allLogsWithCards
        .map { logs ->
            logs.mapNotNull { lwc ->
                lwc.reviewLog.groupName?.takeIf { it.isNotBlank() && it != LearningContexts.CARD_EDITOR }
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
                val matchesText = query.isBlank() || sessionLogs.any { it.matches(query) }
                val matchesOpponent = opponentFilter == null || sessionLogs.any {
                    matchesOpponentFilter(it.reviewLog.opponentId, opponentFilter)
                }
                if (matchesGroup && matchesText && matchesOpponent) {
                    items.add(HistoryItem.Session(session))
                }
            }

            quickGradeLogs.forEach { lwc ->
                val matchesGroup = group == null || lwc.reviewLog.groupName == group
                val matchesText = query.isBlank() || lwc.matches(query)
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

    /** A search finds a review by its card's question or by a concept it tested. */
    private fun ReviewLogWithCard.matches(query: String): Boolean =
        cardQuestion.contains(query, ignoreCase = true) || concepts.any { it.name.contains(query, ignoreCase = true) }

    private fun matchesOpponentFilter(logOpponentId: Long?, filter: Long): Boolean =
        if (filter == OPPONENT_FILTER_NONE) logOpponentId == null else logOpponentId == filter

    fun getReviewLogsForSession(sessionId: Long): Flow<List<ReviewLogWithCard>> =
        combine(repository.getReviewLogsBySession(sessionId), conceptIndex) { logs, index -> logs to index }
            .transform { (logs, index) ->
                val logsWithCards = logs.map { log ->
                    val card = repository.getCardById(log.cardId)
                    ReviewLogWithCard(log, card?.question ?: "Deleted Card", index.testedBy(log))
                }
                emit(logsWithCards)
            }

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

    /**
     * Reassign the opponent on a quick grade -- a review with no session, and so
     * a fight of its own. Metadata-only — does not recompute FSRS state.
     */
    fun updateReviewLogOpponent(reviewLog: ReviewLog, opponentId: Long?) {
        viewModelScope.launch {
            repository.updateReviewLogOpponent(reviewLog, opponentId)
        }
    }

    /** Rewrite what was noted about a session's fight. */
    fun updateSessionNotes(session: PracticeSession, notes: String, imagePaths: List<String>) {
        viewModelScope.launch {
            repository.updateSessionNotes(session.id, notes, imagePaths)
        }
    }

    /**
     * Reassign who a session was fought against, for every review in it at once.
     * Metadata-only — does not recompute FSRS state.
     */
    fun updateSessionOpponent(session: PracticeSession, opponentId: Long?) {
        viewModelScope.launch {
            repository.updateSessionOpponent(session.id, opponentId)
        }
    }

    /**
     * Fold [source] into [target] across all of history. If the history was
     * filtered to [source], the filter follows the reviews to [target].
     */
    fun mergeOpponents(source: Opponent, target: Opponent) {
        viewModelScope.launch {
            if (opponentRepository.mergeOpponents(sourceId = source.id, targetId = target.id) &&
                _opponentFilter.value == source.id
            ) {
                _opponentFilter.value = target.id
            }
        }
    }

    /** Create a new opponent inline from the history editor. Returns the new id, or -1 on conflict. */
    suspend fun createOpponent(name: String, skillMultiplier: Double): Long =
        opponentRepository.insertOpponent(Opponent(name = name, skillMultiplier = skillMultiplier))
}
