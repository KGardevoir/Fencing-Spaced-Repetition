// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.util.CardWithGroupStates
import com.fencing.spacedrepetition.util.ConceptWithStates
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** One review of a concept, as replayed from its cards' history. */
internal data class ConceptReviewEvent(
    val time: Long,
    val grade: Grade,
    val groupName: String?,
    val stabilityMultiplier: Double,
    val groupUid: String? = null
)

/**
 * The arithmetic of learning concepts, apart from the database: turning
 * card reviews into concept reviews and grades, the dot-product draw, a
 * card's staleness, what a due thing needs, and how many cards are ready.
 * CardRepository supplies the data and stores the results.
 */
internal object ConceptScheduling {

    /** What has a need in a dot-product draw: a concept, or an untagged card standing for itself. */
    internal sealed interface NeedKey {
        data class OfConcept(val conceptId: Long) : NeedKey
        data class OfCard(val cardId: Long) : NeedKey
    }

    /**
     * One card a dot-product draw took: its [score] (the need it met), and
     * each term of the dot product that was more than 0 -- share times need,
     * by what had the need.
     */
    internal data class DotPick<K>(val cardId: Long, val score: Double, val terms: Map<K, Double>)

    /**
     * The concept reviews a set of card review logs implies, oldest first.
     *
     * The reviews of one practice session are one fight, so all of a
     * session's logs make a single concept review, graded with the
     * rounded mean of its cards' grades, each weighted by how much of the
     * concept its card contains ([weightOf], by card id) -- a concept on
     * three cards of one session is reviewed once, not three times in the
     * same minute. A log outside any session (graded from the card editor,
     * or imported without one) is a review of its own. Skips say nothing
     * and are left out, as are cards that contain none of the concept
     * (weight 0); a session of nothing else is no review at all.
     */
    internal fun conceptReviewEvents(
        logs: List<ReviewLog>,
        weightOf: (cardId: Long) -> Double = { 1.0 }
    ): List<ConceptReviewEvent> =
        logs.filter { it.grade != Grade.SKIP.value && Grade.fromValue(it.grade) != null && weightOf(it.cardId) > 0.0 }
            .groupBy { log -> log.sessionId?.let { "s$it" } ?: "l${log.id}:${log.reviewTime}" }
            .values
            .map { session ->
                val first = session.minBy { it.reviewTime }
                ConceptReviewEvent(
                    time = first.reviewTime,
                    grade = combineWeightedConceptGrades(
                        session.map { Grade.fromValue(it.grade)!! to weightOf(it.cardId) }
                    ),
                    groupName = first.groupName,
                    groupUid = first.groupUid,
                    stabilityMultiplier = first.stabilityMultiplier
                )
            }
            .sortedBy { it.time }

    /**
     * The grade a concept gets from the grades of the cards carrying it:
     * their mean, rounded half up to the nearest grade. Callers leave
     * skipped cards out.
     */
    internal fun combineConceptGrades(grades: List<Grade>): Grade =
        combineWeightedConceptGrades(grades.map { it to 1.0 })

    /**
     * The weighted mean of [grades] -- each grade with how much of the
     * concept its card contains -- rounded half up to the nearest grade.
     * Weights need not add up to anything; at least one must be above 0.
     */
    internal fun combineWeightedConceptGrades(grades: List<Pair<Grade, Double>>): Grade {
        require(grades.isNotEmpty() && grades.none { it.first == Grade.SKIP })
        val total = grades.sumOf { it.second }
        require(total > 0.0) { "No card contains any of the concept" }
        val mean = grades.sumOf { (grade, weight) -> grade.value * weight } / total
        val rounded = kotlin.math.floor(mean + 0.5).toInt().coerceIn(Grade.AGAIN.value, Grade.EASY.value)
        return Grade.fromValue(rounded)!!
    }

    /**
     * The greedy dot-product draw. Each card in [order] is a vector of
     * shares ([vectors]); each key has a need. Repeatedly take the card
     * whose dot product with the remaining need is largest (the first in
     * [order] on a tie), then lower each of its keys' need by its share,
     * not below 0. Stops at [limit] cards, or when no card meets any need.
     */
    internal fun <K> greedyDotProduct(
        order: List<Long>,
        vectors: Map<Long, Map<K, Double>>,
        need: Map<K, Double>,
        limit: Int,
        /** Multiplies a card's dot product when choosing; the need it meets is unaffected. */
        factor: (cardId: Long) -> Double = { 1.0 }
    ): List<DotPick<K>> {
        val remaining = need.toMutableMap()
        val left = order.toMutableList()
        val picks = mutableListOf<DotPick<K>>()
        while (picks.size < limit && left.isNotEmpty()) {
            var best: Long? = null
            var bestScore = 0.0
            for (cardId in left) {
                val met = vectors[cardId].orEmpty().entries.sumOf { (key, share) -> share * (remaining[key] ?: 0.0) }
                if (met <= 0.0) continue
                val score = met * factor(cardId)
                if (score > bestScore + 1e-12) { best = cardId; bestScore = score }
            }
            val chosen = best ?: break
            val vector = vectors[chosen].orEmpty()
            val terms = vector.mapNotNull { (key, share) ->
                (share * (remaining[key] ?: 0.0)).takeIf { it > 0.0 }?.let { key to it }
            }.toMap()
            // The need it met, before staleness: what the card actually covers.
            picks += DotPick(chosen, terms.values.sum(), terms)
            vector.forEach { (key, share) -> remaining[key] = ((remaining[key] ?: 0.0) - share).coerceAtLeast(0.0) }
            left.remove(chosen)
        }
        return picks
    }

    /** How many days of rest make a card about two-thirds of the way back to full staleness. */
    internal const val STALENESS_DAYS = 7.0

    /**
     * A card's staleness factor in a dot-product draw: 0.5 just after it
     * was graded, approaching 1 as days pass (about 0.82 after a week,
     * 0.98 after three), and 1 for a card never graded ([daysSinceGraded] null).
     */
    internal fun stalenessFactor(daysSinceGraded: Double?): Double =
        if (daysSinceGraded == null) 1.0
        else 0.5 + 0.5 * (1.0 - kotlin.math.exp(-daysSinceGraded.coerceAtLeast(0.0) / STALENESS_DAYS))

    /**
     * The need of something due in a dot-product draw: 1 minus its
     * predicted recall at [now] -- 1 if never learnt -- or 0 when it is
     * not due yet.
     */
    internal fun needOf(
        state: FsrsState, now: Long,
        retrievability: (elapsedDays: Double, stability: Double) -> Double
    ): Double {
        if (state.nextReview > now) return 0.0
        val elapsedDays = if (state.lastReview <= 0L) 0.0 else (now - state.lastReview).toDouble() / FsrsState.DAY_MILLIS
        return 1.0 - retrievability(elapsedDays, state.stability)
    }

    /**
     * What a dot-product draw over [cards] in one learning context works
     * from: each card's vector of shares (an untagged card is all of
     * itself), the need of every concept and untagged card, and each card's
     * own progress there (new in a learning group it was never practised in).
     */
    internal class DrawInputs(
        val vectors: Map<Long, Map<NeedKey, Double>>,
        val need: Map<NeedKey, Double>,
        val ownStates: Map<Long, FsrsState>
    )

    /**
     * @param cardStates cards' learning-group states, by (card, learning group).
     * @param shares each card's share of each of its concepts.
     * @param conceptStates each concept's state in this context; one missing
     *   is new there, so fully needed.
     */
    internal fun drawInputs(
        cards: List<Card>,
        learningGroupId: Long?,
        cardStates: Map<Pair<Long, Long>, CardLearningState>,
        shares: Map<Pair<Long, Long>, Double>,
        conceptStates: Map<Long, ConceptLearningState>,
        now: Long,
        retrievability: (elapsedDays: Double, stability: Double) -> Double
    ): DrawInputs {
        val conceptsByCard = shares.keys.groupBy({ it.first }, { it.second })
        val ownStates = cards.associate { card ->
            card.id to if (learningGroupId == null) card.fsrs else cardStates[card.id to learningGroupId]?.fsrs ?: FsrsState()
        }
        val need = HashMap<NeedKey, Double>()
        val vectors = cards.associate { card ->
            val concepts = conceptsByCard[card.id]
            card.id to if (concepts != null) {
                concepts.associate { conceptId ->
                    val key: NeedKey = NeedKey.OfConcept(conceptId)
                    need[key] = needOf(conceptStates[conceptId]?.fsrs ?: FsrsState(), now, retrievability)
                    key to shares.getValue(card.id to conceptId)
                }
            } else {
                val key: NeedKey = NeedKey.OfCard(card.id)
                need[key] = needOf(ownStates.getValue(card.id), now, retrievability)
                mapOf(key to 1.0)
            }
        }
        return DrawInputs(vectors, need, ownStates)
    }

    /**
     * How many of [cards] are ready for practice at [now] in learning
     * context [learningGroupId] (null is Global): card by card, those due by
     * their own state there (Global's for a card never practised in the
     * learning group, as that draw does); by concept, how many cards the
     * dot-product draw takes, with no limit, to meet all the need.
     */
    internal fun countDue(
        cards: List<Card>,
        learningGroupId: Long?,
        byConcept: Boolean,
        cardStates: Map<Pair<Long, Long>, CardLearningState>,
        shares: Map<Pair<Long, Long>, Double>,
        conceptStates: Map<Long, ConceptLearningState>,
        now: Long,
        retrievability: (elapsedDays: Double, stability: Double) -> Double
    ): Int {
        if (!byConcept) return cards.count { card ->
            val due = learningGroupId?.let { cardStates[card.id to it] }?.nextReview ?: card.nextReview
            due <= now
        }
        val inputs = drawInputs(cards, learningGroupId, cardStates, shares, conceptStates, now, retrievability)
        return greedyDotProduct(cards.map { it.id }.sorted(), inputs.vectors, inputs.need, cards.size).size
    }
}
