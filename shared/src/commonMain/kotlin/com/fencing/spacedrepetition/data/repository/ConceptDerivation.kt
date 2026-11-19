// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.data.repository.ConceptScheduling.conceptReviewEvents
import kotlinx.coroutines.flow.first

/**
 * Concept progress, derived from card history.
 *
 * A concept's learning state is not something a concept accumulates on
 * its own: it is a function of the review history of the cards that carry
 * it. Every learning context's state is rebuilt by replaying those cards'
 * reviews, oldest first, so tagging a card with a concept immediately
 * gives the concept what that card has already been through, untagging
 * takes it away again, and a concept whose cards have no history is
 * simply new. What is stored on the concept and in
 * concept_learning_state is that replay's result, kept so that drawing a
 * session does not have to replay anything.
 *
 * Which context a review belongs to is read from the group its log
 * names, as it stands now: a group in a learning group counts towards
 * that learning group, a group learning Global -- or no group, or a grade
 * from the card editor -- towards Global. A log naming a group that no
 * longer exists cannot be placed and is left out.
 *
 * The state is derived for every context the history reaches, whether
 * or not that context schedules by concept; the switch only decides
 * whether a session draws by it.
 */
class ConceptDerivation internal constructor(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao,
    private val conceptDao: ConceptDao,
    private val scheduler: Scheduler
) {
    /** Rederive every concept carried by any of [cardIds]. */
    suspend fun rederiveConceptsForCards(cardIds: Collection<Long>) {
        if (cardIds.isEmpty()) return
        val ids = conceptDao.getAllCardConceptCrossRefsSync()
            .filter { it.cardId in cardIds }
            .map { it.conceptId }
            .toSet()
        if (ids.isNotEmpty()) rederiveConcepts(ids)
    }

    /**
     * Make [conceptIds] exactly the concepts [cardId] carries, each with its
     * weight in [weights] (1 when absent), and rederive every concept that changed.
     */
    suspend fun setCardConcepts(
        cardId: Long,
        conceptIds: Collection<Long>,
        weights: Map<Long, Double> = emptyMap()
    ) {
        val before = conceptDao.getCardConceptLinksSync(cardId).associate { it.conceptId to it.weight }
        val after = conceptIds.associateWith { CardConceptCrossRef.sanitize(weights[it]) }
        if (before == after) return
        conceptDao.deleteAllConceptsForCard(cardId)
        after.forEach { (conceptId, weight) -> conceptDao.insertCardConceptCrossRef(CardConceptCrossRef(cardId, conceptId, weight)) }
        // Shares are proportions within the card, so any change to its weights
        // can move every one of its concepts: rederive them all.
        rederiveConcepts(before.keys + after.keys)
    }

    /**
     * Rebuild the state of [conceptIds] (every concept when null) in every
     * learning context from their cards' review history.
     *
     * @param onlyWithHistory leave a concept whose cards have no history at
     *   all as it is, rather than making it new. For imports, which may bring
     *   a concept's state in a file without the history behind it.
     */
    suspend fun rederiveConcepts(conceptIds: Collection<Long>? = null, onlyWithHistory: Boolean = false) {
        val concepts = if (conceptIds == null) conceptDao.getAllConceptsSync()
            else conceptDao.getConceptsByIds(conceptIds.toList())
        if (concepts.isEmpty()) return
        // Each concept's cards, with each card's share of the concept.
        val links = conceptDao.getAllCardConceptCrossRefsSync()
        val shares = conceptShares(links)
        val cardsByConcept = links
            .groupBy({ it.conceptId }, { it.cardId to shares.getValue(it.cardId to it.conceptId) })
            .mapValues { it.value.toMap() }
        val logsByCard = reviewLogDao.getAllReviewLogsSync().groupBy { it.cardId }
        val contexts = LearningContexts(groupDao.getAllGroups().first(), groupDao.getAllLearningGroups().first())
        // When each card's progress was last reset, per context: the card's
        // own for Global, its learning-group state's for each learning group.
        val globalResets = cardDao.getAllCardsAsList().associate { it.id to it.lastReset }
        val learningGroupResets = groupDao.getAllLearningStatesSync()
            .associate { (it.cardId to it.learningGroupId) to it.lastReset }
        fun resetOf(cardId: Long, context: LearningContext): Long = when (val lg = context.learningGroupId) {
            null -> globalResets[cardId] ?: 0L
            else -> learningGroupResets[cardId to lg] ?: 0L
        }

        concepts.forEach { concept ->
            val weights = cardsByConcept[concept.id].orEmpty()
            val logs = weights.keys.flatMap { logsByCard[it].orEmpty() }
                .filter { it.grade != Grade.SKIP.value }
            if (onlyWithHistory && logs.isEmpty()) return@forEach

            val byContext = logs.mapNotNull { log ->
                val context = contexts.ofReview(log) ?: return@mapNotNull null
                // Only progress since the card was last reset in that context.
                if (log.reviewTime <= resetOf(log.cardId, context)) null else context to log
            }.groupBy({ it.first }, { it.second })

            val weightOf = { cardId: Long -> weights[cardId] ?: 1.0 }
            val global = replayConceptHistory(concept.id, 0L, byContext[LearningContext.GLOBAL].orEmpty(), contexts, weightOf)
                ?: ConceptLearningState(concept.id, 0L)
            conceptDao.updateConcept(concept.withGlobalState(global))

            conceptDao.deleteAllLearningStatesForConcept(concept.id)
            byContext.forEach { (key, contextLogs) ->
                val learningGroupId = key.learningGroupId ?: return@forEach
                replayConceptHistory(concept.id, learningGroupId, contextLogs, contexts, weightOf)
                    ?.let { conceptDao.upsertConceptLearningState(it) }
            }
        }
    }

    /** Replay one context's logs into a state, or null when they hold no review. */
    private suspend fun replayConceptHistory(
        conceptId: Long,
        learningGroupId: Long,
        logs: List<ReviewLog>,
        contexts: LearningContexts,
        weightOf: (cardId: Long) -> Double
    ): ConceptLearningState? {
        val events = conceptReviewEvents(logs, weightOf)
        if (events.isEmpty()) return null
        var state = ConceptLearningState(conceptId, learningGroupId)
        events.forEach { event ->
            val groupId = contexts.groupOf(event.groupUid, event.groupName)?.id
            state = scheduleConceptState(state, event.grade, event.time, groupId, event.stabilityMultiplier)
        }
        return state
    }

    /**
     * One FSRS step of a concept at [now].
     *
     * Fuzz is always off here: a replay has to land on the same state every
     * time it is run, or rederiving would move concepts about at random.
     */
    private suspend fun scheduleConceptState(
        state: ConceptLearningState,
        grade: Grade,
        now: Long,
        groupId: Long?,
        stabilityMultiplier: Double
    ): ConceptLearningState =
        state.withFsrs(scheduler.schedule(state.fsrs, grade, now, groupId, stabilityMultiplier, fuzzing = false)).copy(modified = now)
}
