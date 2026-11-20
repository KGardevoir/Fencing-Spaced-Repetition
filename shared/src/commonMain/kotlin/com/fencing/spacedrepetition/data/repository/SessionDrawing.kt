// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.data.repository.ConceptScheduling.NeedKey
import com.fencing.spacedrepetition.data.repository.ConceptScheduling.countDue
import com.fencing.spacedrepetition.data.repository.ConceptScheduling.drawInputs
import com.fencing.spacedrepetition.data.repository.ConceptScheduling.greedyDotProduct
import com.fencing.spacedrepetition.data.repository.ConceptScheduling.stalenessFactor
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Which cards a session draws -- card by card, or by concept -- and how many
 * are due, counted the same way.
 */
class SessionDrawing internal constructor(
    private val cardDao: CardDao,
    private val groupDao: GroupDao,
    private val conceptDao: ConceptDao,
    private val preferences: SchedulingPreferences,
    private val scheduler: Scheduler
) {
    /** Every card, shuffled when Global randomises: the top-up for all-cards practice. */
    private suspend fun allCardsShuffledIfSet(): List<Card> {
        val cards = cardDao.getAllCardsSync()
        return if (preferences.randomizeDueCards.first()) cards.shuffled() else cards
    }

    suspend fun getDueCards(limit: Int = 100): List<Card> {
        val shouldRandomize = preferences.randomizeDueCards.first()
        // When randomizing, fetch all due cards so we can sample from the full pool
        val cards = if (shouldRandomize) {
            cardDao.getDueCards(limit = Int.MAX_VALUE)
        } else {
            cardDao.getDueCards(limit = limit)
        }
        return if (shouldRandomize) {
            val bucketHours = preferences.randomizeBucketHours.first()
            randomizeCardsByBucket(cards, bucketHours).take(limit)
        } else {
            cards
        }
    }

    /** How many cards are due in all-cards practice (Global); see [dueCount]. */
    fun getDueCardCount(): Flow<Int> = dueCount(null)

    /**
     * How many cards are ready for practice in a session over the group's
     * cards ([groupId]), or every card (null), in the learning context the
     * session practises in -- counted the way that context draws.
     *
     * Card by card: the cards due by their own state there. By concept: how
     * many cards the dot-product draw would take, with no session limit,
     * before every due concept's and untagged card's need is met -- so a due
     * concept on thirty cards counts as the one or two it takes, not thirty.
     *
     * Recounted whenever cards, groups, concepts or their states change, and
     * every minute, so cards coming due while the screen is open are counted.
     * Disabled cards never are.
     */
    fun dueCount(groupId: Long?): Flow<Int> = combine(
        combine(cardDao.getAllCardsWithGroups(), groupDao.getAllLearningStates(), groupDao.getAllLearningGroups()) { a, b, c -> Triple(a, b, c) },
        combine(conceptDao.getAllConcepts(), conceptDao.getAllCardConceptCrossRefs(), conceptDao.getAllConceptLearningStates()) { a, b, c -> Triple(a, b, c) },
        preferences.globalConceptScheduling,
        minuteTicker()
    ) { (cardsWithGroups, cardStates, learningGroups), (concepts, links, conceptStates), globalByConcept, now ->
        val contexts = LearningContexts(cardsWithGroups.flatMap { it.groups }.distinctBy { it.id }, learningGroups)
        val pool = if (groupId == null) cardsWithGroups.map { it.card }
            else cardsWithGroups.filter { cwg -> cwg.groups.any { it.id == groupId } }.map { it.card }
        val learningGroupId = contexts.ofGroupId(groupId)
        val byConcept = contexts.schedulesByConcept(learningGroupId, globalByConcept)
        val enabled = pool.filter { !it.isDisabled }
        val enabledIds = enabled.map { it.id }.toSet()
        countDue(
            enabled, learningGroupId, byConcept,
            cardStates.associateBy { it.cardId to it.learningGroupId },
            conceptShares(links.filter { it.cardId in enabledIds }),
            if (learningGroupId == null) concepts.associate { it.id to it.globalState() }
            else conceptStates.filter { it.learningGroupId == learningGroupId }.associateBy { it.conceptId },
            now,
            scheduler::retrievability
        )
    }

    /** The time now, again every minute. */
    private fun minuteTicker(): Flow<Long> = flow {
        while (true) {
            emit(Time.now())
            delay(60_000L)
        }
    }


    suspend fun getDueCardsByGroup(groupId: Long, limit: Int = 100): List<Card> {
        val learningGroup = scheduler.learningGroupFor(groupId)
        val shouldRandomize = learningGroup?.randomizeDueCards ?: preferences.randomizeDueCards.first()
        val cards = if (shouldRandomize) {
            cardDao.getDueCardsByGroup(groupId, limit = Int.MAX_VALUE)
        } else {
            cardDao.getDueCardsByGroup(groupId, limit = limit)
        }
        return if (shouldRandomize) {
            val bucketHours = learningGroup?.randomizeBucketHours ?: preferences.randomizeBucketHours.first()
            randomizeCardsByBucket(cards, bucketHours).take(limit)
        } else {
            cards
        }
    }

    suspend fun getCardsByGroupSync(groupId: Long): List<Card> {
        val cards = cardDao.getCardsByGroupSync(groupId)
        val shouldRandomize = scheduler.learningGroupFor(groupId)?.randomizeDueCards ?: preferences.randomizeDueCards.first()
        return if (shouldRandomize) {
            cards.shuffled()
        } else {
            cards
        }
    }

    /** How many of [groupId]'s cards are due; see [dueCount]. */
    fun getDueCardCountByGroup(groupId: Long): Flow<Int> = dueCount(groupId)


    /**
     * Draw the cards for a session: by concept where the session's learning
     * context has concept scheduling on ([drawCardsByConcept]), otherwise
     * card by card, exactly as before concepts -- due cards first, topped up
     * with the rest of the pool.
     */
    suspend fun drawCards(limit: Int, groupId: Long?): List<Card> = drawSession(limit, groupId).map { it.card }

    /** [drawCards], with why each card was drawn -- recorded on its review log. */
    suspend fun drawSession(limit: Int, groupId: Long?): List<DrawnCard> {
        if (scheduler.usesConcepts(scheduler.learningGroupIdFor(groupId))) return drawSessionByConcept(limit, groupId)
        val dueCards = if (groupId != null) getDueCardsByGroup(groupId, limit) else getDueCards(limit)
        val drawn = if (dueCards.size >= limit) dueCards.take(limit) else {
            val allCards = if (groupId != null) getCardsByGroupSync(groupId) else allCardsShuffledIfSet()
            allCards.take(limit)
        }
        val dueIds = dueCards.map { it.id }.toSet()
        return drawn.map {
            DrawnCard(
                it,
                CardSelection(SelectionMode.CARD, if (it.id in dueIds) SelectionReason.DUE else SelectionReason.EXTRA)
            )
        }
    }

    /** How a session in [groupId] is drawn now: by concept or card by card. */
    suspend fun selectionModeFor(groupId: Long?): SelectionMode =
        if (scheduler.usesConcepts(scheduler.learningGroupIdFor(groupId))) SelectionMode.CONCEPT else SelectionMode.CARD

    /**
     * Every concept's state in one learning context: Global when
     * [learningGroupId] is null, else that learning group's. A concept never
     * practised in the learning group is new there -- the learning group is
     * a separate set of progress, so it does not inherit Global's.
     */
    suspend fun conceptStatesIn(learningGroupId: Long?): Map<Long, ConceptLearningState> {
        val concepts = conceptDao.getAllConceptsSync()
        if (learningGroupId == null) return concepts.associate { it.id to it.globalState() }
        val stored = conceptDao.getConceptLearningStatesForLearningGroup(learningGroupId)
            .associateBy { it.conceptId }
        return concepts.associate { concept ->
            concept.id to (stored[concept.id] ?: ConceptLearningState(concept.id, learningGroupId))
        }
    }

    /**
     * Draw the cards for a session, scheduling by concept, by dot product.
     *
     * Every concept, and every untagged card, has a **need**: 1 minus FSRS's
     * predicted probability of recalling it right now, in this learning
     * context -- 1 for one never learnt -- and 0 if it is not due yet. Every
     * card is a vector of its **shares** of its concepts (its weights
     * normalised to add to 1); an untagged card is all of itself.
     *
     * A card's score is the dot product of the two -- the need it would
     * meet -- times its **staleness**: 0.5 for a card graded just now in this
     * context, rising towards 1 the longer since ([stalenessFactor]); 1 for
     * one never graded here. So of cards meeting much the same need, the one
     * left alone longest wins, and the same cards are not drawn every time.
     * The highest-scoring card is taken; then each of its concepts' need
     * drops by the card's share of it (never below 0), so a card half about
     * a concept leaves half of that concept's need for another card. Repeat
     * until the session is full or nothing left meets any need. Ties go to
     * the lowest id, or at random when the group randomises.
     *
     * A session the need does not fill is topped up with the rest of the
     * pool, soonest due first -- additional study, as before.
     */
    internal suspend fun drawCardsByConcept(limit: Int, groupId: Long?): List<Card> =
        drawSessionByConcept(limit, groupId).map { it.card }

    private suspend fun drawSessionByConcept(limit: Int, groupId: Long?): List<DrawnCard> {
        if (limit <= 0) return emptyList()
        val pool = if (groupId != null) cardDao.getCardsByGroupSync(groupId) else cardDao.getAllCardsSync()
        if (pool.isEmpty()) return emptyList()

        // How a session is drawn is the learning group's to say, as for cards.
        val learningGroup = scheduler.learningGroupFor(groupId)
        val learningGroupId = learningGroup?.id
        val shouldRandomize = learningGroup?.randomizeDueCards ?: preferences.randomizeDueCards.first()

        val poolIds = pool.map { it.id }.toSet()
        val shares = conceptShares(conceptDao.getAllCardConceptCrossRefsSync().filter { it.cardId in poolIds })
        val conceptsByCard = shares.keys.groupBy({ it.first }, { it.second })
        val conceptStates = conceptStatesIn(learningGroupId)
        val cardStates = if (learningGroupId == null) emptyMap() else groupDao.getAllLearningStatesSync()
            .filter { it.learningGroupId == learningGroupId && it.cardId in poolIds }
            .associateBy { it.cardId to it.learningGroupId }

        val now = Time.now()
        val inputs = drawInputs(pool, learningGroupId, cardStates, shares, conceptStates, now, scheduler::retrievability)
        // Staleness: how long since each card was last graded here.
        val staleness = inputs.ownStates.mapValues { (_, state) ->
            stalenessFactor(if (state.lastReview <= 0L) null else (now - state.lastReview).toDouble() / FsrsState.DAY_MILLIS)
        }
        val order = if (shouldRandomize) pool.map { it.id }.shuffled() else pool.map { it.id }.sorted()
        val picks = greedyDotProduct(order, inputs.vectors, inputs.need, limit) { cardId -> staleness[cardId] ?: 1.0 }

        val cardsById = pool.associateBy { it.id }
        val selected = LinkedHashMap<Long, Card>()
        picks.forEach { selected[it.cardId] = cardsById.getValue(it.cardId) }
        val pickById = picks.associateBy { it.cardId }

        // A card's concept coming due soonest (the larger share on a tie):
        // what puts it first in line for the top-up.
        fun soonestConcept(cardId: Long): Long? = conceptsByCard[cardId]?.minWithOrNull(
            compareBy<Long> { conceptStates[it]?.nextReview ?: 0L }
                .thenByDescending { shares.getValue(cardId to it) }
                .thenBy { it }
        )
        if (selected.size < limit) {
            fun dueOf(card: Card): Long = soonestConcept(card.id)?.let { conceptStates[it]?.nextReview ?: 0L }
                ?: inputs.ownStates.getValue(card.id).nextReview
            val rest = pool.filter { it.id !in selected }.sortedBy(::dueOf)
            val fill = if (shouldRandomize) rest.shuffled() else rest
            fill.take(limit - selected.size).forEach { selected[it.id] = it }
        }

        val conceptUids = conceptDao.getAllConceptsSync().associate { it.id to it.uid }
        return selected.values.map { card ->
            val pick = pickById[card.id]
            // The concepts whose need it met, and the one it met most of --
            // or, for extra study, the concept coming due soonest.
            val conceptTerms = pick?.terms.orEmpty().mapNotNull { (key, term) ->
                (key as? NeedKey.OfConcept)?.let { it.conceptId to term }
            }
            val trigger = if (pick != null) {
                conceptTerms.maxWithOrNull(compareBy<Pair<Long, Double>> { it.second }.thenByDescending { it.first })?.first
            } else soonestConcept(card.id)
            DrawnCard(
                card,
                CardSelection(
                    SelectionMode.CONCEPT,
                    if (pick != null) SelectionReason.DUE else SelectionReason.EXTRA,
                    conceptTerms.mapNotNull { conceptUids[it.first] }.sorted(),
                    trigger?.let { conceptUids[it] }
                )
            )
        }
    }


    /**
     * Randomize cards by grouping them into time buckets of the given size.
     * Cards within the same bucket are shuffled randomly; bucket ordering
     * is preserved so more-overdue cards still come first.
     */
    private fun randomizeCardsByBucket(cards: List<Card>, bucketHours: Int): List<Card> =
        shuffleByBucket(cards, bucketHours) { it.nextReview }

    companion object {
        /**
         * Shuffle [items] within time buckets of [bucketHours], keeping the
         * buckets in order so the more overdue still come first.
         */
        internal fun <T> shuffleByBucket(items: List<T>, bucketHours: Int, due: (T) -> Long): List<T> {
            if (items.isEmpty()) return items
            val millisecondsPerBucket = bucketHours.coerceAtLeast(1) * 60 * 60 * 1000L
            return items.groupBy { due(it) / millisecondsPerBucket }
                .entries
                .sortedBy { it.key }
                .flatMap { (_, inBucket) -> inBucket.shuffled() }
        }
    }
}
