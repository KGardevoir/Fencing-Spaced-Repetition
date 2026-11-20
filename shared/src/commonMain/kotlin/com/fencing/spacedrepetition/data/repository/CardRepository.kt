// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Cards, their progress and their history. The work on them is split by job,
 * each a property here sharing these DAOs: [grading], [drawing] sessions and
 * due counts, [conceptDerivation] from history, and the [archive] for import
 * and export.
 */
class CardRepository(
    private val cardDao: CardDao,
    private val sessionDao: PracticeSessionDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao,
    private val opponentDao: OpponentDao,
    private val preferences: SchedulingPreferences,
    private val conceptDao: ConceptDao
) {
    internal val scheduler = Scheduler(groupDao, preferences)
    val conceptDerivation = ConceptDerivation(cardDao, reviewLogDao, groupDao, conceptDao, scheduler)
    val grading = CardGrading(cardDao, reviewLogDao, groupDao, opponentDao, scheduler, conceptDerivation)
    val drawing = SessionDrawing(cardDao, groupDao, conceptDao, preferences, scheduler)
    val archive = CardArchive(cardDao, sessionDao, reviewLogDao, groupDao, conceptDao)

    // Card operations
    fun getAllCards(): Flow<List<Card>> = cardDao.getAllCards()

    suspend fun getAllCardsSync(): List<Card> = cardDao.getAllCardsSync()

    suspend fun getCardById(cardId: Long): Card? = cardDao.getCardById(cardId)

    suspend fun insertCard(card: Card): Long = cardDao.insertCard(card)

    /** Each of [cardIds]' concepts with how much of each the card contains, by card id, by name. */
    suspend fun conceptsOfCards(cardIds: Collection<Long>): Map<Long, List<Pair<Concept, Double>>> {
        val ids = cardIds.toSet()
        val byId = conceptDao.getAllConceptsSync().associateBy { it.id }
        return conceptDao.getAllCardConceptCrossRefsSync()
            .filter { it.cardId in ids }
            .groupBy({ it.cardId }, { link -> byId[link.conceptId]?.let { it to link.weight } })
            .mapValues { (_, concepts) -> concepts.filterNotNull().sortedBy { it.first.name.lowercase() } }
    }

    suspend fun findCardByQuestion(question: String): Card? = cardDao.findCardByQuestion(question)

    suspend fun updateCard(card: Card) = cardDao.updateCard(card)

    /** Deletes the card and its history; its concepts are rederived without either. */
    suspend fun deleteCardById(cardId: Long) {
        val conceptIds = conceptDao.getConceptsForCardSync(cardId).map { it.id }
        cardDao.deleteCardById(cardId)
        reviewLogDao.deleteReviewLogsByCard(cardId)
        if (conceptIds.isNotEmpty()) conceptDerivation.rederiveConcepts(conceptIds)
    }

    suspend fun resetCardState(cardId: Long, resetGroupStates: Boolean = false): Card? {
        val card = cardDao.getCardById(cardId) ?: return null
        val now = Time.now()
        // Back to new; reviews before now stop counting towards anything derived.
        val resetCard = card.withFsrs(FsrsState()).copy(modified = now, lastReset = now)
        cardDao.updateCard(resetCard)

        // Optionally reset the learning-group progress too: every learning
        // group, held or not, gets a new state stamped with the reset, so the
        // history from before it is set aside there as well.
        if (resetGroupStates) {
            groupDao.getAllLearningGroups().first().forEach { learningGroup ->
                groupDao.insertLearningState(
                    CardLearningState(cardId, learningGroup.id, modified = now, lastReset = now)
                )
            }
        }
        conceptDerivation.rederiveConceptsForCards(listOf(cardId))
        return resetCard
    }

    /** Resets the state [cardId] keeps in the learning group that [groupId] practises against. */
    suspend fun resetCardStateInGroup(cardId: Long, groupId: Long) {
        val learningGroupId = groupDao.getGroupById(groupId)?.learningGroupId ?: return
        resetCardStateInLearningGroup(cardId, learningGroupId)
    }

    /**
     * Back to new in one learning group, stamped with when, so the history from
     * before the reset stops counting there. Written whether or not the card
     * held a state in it yet: a reset is a fact about the progress, not the row.
     */
    suspend fun resetCardStateInLearningGroup(cardId: Long, learningGroupId: Long) {
        val now = Time.now()
        groupDao.insertLearningState(CardLearningState(cardId, learningGroupId, modified = now, lastReset = now))
        conceptDerivation.rederiveConceptsForCards(listOf(cardId))
    }

    fun getCardCount(): Flow<Int> = cardDao.getCardCount()

    fun getPracticeHistoryStats(startTime: Long): Flow<PracticeHistoryStats> =
        reviewLogDao.getPracticeHistoryStats(startTime)

    // History operations
    fun getCompletedSessions(): Flow<List<PracticeSession>> = sessionDao.getCompletedSessions()

    fun getReviewLogsBySession(sessionId: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsBySession(sessionId)

    fun getReviewLogsWithoutSession(): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsWithoutSession()

    fun getReviewLogsByCard(cardId: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsByCard(cardId)

    fun getAllReviewLogs(): Flow<List<ReviewLog>> = reviewLogDao.getAllReviewLogs()

    suspend fun getAllReviewLogsSync(): List<ReviewLog> = reviewLogDao.getAllReviewLogsSync()

    suspend fun updateReviewLog(reviewLog: ReviewLog) {
        reviewLogDao.updateReviewLog(reviewLog)
    }

    // Group-aware card operations
    fun getAllCardsWithGroups(): Flow<List<CardWithGroups>> = cardDao.getAllCardsWithGroups()

    fun getCardCountByGroup(groupId: Long): Flow<Int> = cardDao.getCardCountByGroup(groupId)

    suspend fun insertCardWithGroups(card: Card, groupIds: List<Long>): Long {
        val cardId = cardDao.insertCard(card)
        groupIds.forEach { groupId ->
            groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
        }
        return cardId
    }

    suspend fun updateCardGroups(cardId: Long, groupIds: List<Long>) {
        groupDao.deleteAllGroupsForCard(cardId)
        groupIds.forEach { groupId ->
            groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
        }
    }

    // ---------------------------------------------------------------------
    // Learning concepts
    // ---------------------------------------------------------------------

    // Practice session operations
    suspend fun createPracticeSession(cardIds: List<Long>): Long {
        val session = PracticeSession(
            cardIds = cardIds.joinToString(","),
            completed = false
        )
        return sessionDao.insertSession(session)
    }

    suspend fun getSessionById(sessionId: Long): PracticeSession? = sessionDao.getSessionById(sessionId)

    suspend fun completeSession(
        sessionId: Long,
        grades: List<Grade>,
        notes: String = "",
        imagePaths: List<String> = emptyList()
    ) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val updatedSession = session.copy(
            endTime = Time.now(),
            completed = true,
            grades = grades.joinToString(",") { it.value.toString() },
            notes = notes,
            imagePaths = imagePaths.joinToString(",")
        )
        sessionDao.updateSession(updatedSession)
    }

    /** Rewrite what was noted about a session's fight, from the history screen. */
    suspend fun updateSessionNotes(sessionId: Long, notes: String, imagePaths: List<String>) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(notes = notes, imagePaths = imagePaths.joinToString(",")))
    }

    /**
     * Reassign the opponent of every review in a session at once.
     *
     * The opponent belongs to the fight, and the session is the fight, so it is
     * changed for the whole session or not at all. Metadata only, like any
     * history correction: the reviews were scheduled with the multiplier they
     * recorded, and they keep it.
     */
    suspend fun updateSessionOpponent(sessionId: Long, opponentId: Long?) {
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }
        reviewLogDao.updateOpponentForSession(sessionId, opponentId, opponentUid)
    }

    /** Set one review's opponent, by local id and by identifier together. */
    suspend fun updateReviewLogOpponent(reviewLog: ReviewLog, opponentId: Long?) {
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }
        reviewLogDao.updateReviewLog(reviewLog.copy(opponentId = opponentId, opponentUid = opponentUid))
    }

    /**
     * Bring every review's opponent reference into line after opponents or
     * history arrive by import: see [ReviewLogDao.fillOpponentUids] and
     * [ReviewLogDao.relinkOpponentIds].
     */
    suspend fun reconcileOpponentReferences() {
        reviewLogDao.fillOpponentUids()
        reviewLogDao.relinkOpponentIds()
    }

    fun getSessionByIdFlow(sessionId: Long): Flow<PracticeSession?> =
        sessionDao.getSessionByIdFlow(sessionId)

    suspend fun getAllSessionsSync(): List<PracticeSession> = sessionDao.getAllSessionsSync()

    /** The card groups, for naming them as they are now (history, by stable identifier). */
    fun getAllGroups(): Flow<List<Group>> = groupDao.getAllGroups()

    // Learning group state operations
    /** Saves a card's progress in a learning group, held there before or not. */
    suspend fun saveLearningState(learningState: CardLearningState) =
        groupDao.insertLearningState(learningState)
}
