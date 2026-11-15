// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.algorithm

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.GROUP_NAME_CARD_EDIT

/**
 * How retention is going, at every level the app keeps learning state:
 * the whole collection, each learning context (Global and every learning
 * group), each card group, and each card within each learning context.
 *
 * Two different measures are reported side by side, because they answer
 * different questions:
 *
 * - **Measured recall** -- of the reviews where a card had been seen before,
 *   the share that were not graded Again. This is what actually happened.
 * - **Predicted retention** -- FSRS's own estimate of the probability of
 *   recall. "Now" uses each card's stability and the time since its last
 *   review; in a trend it is the estimate at the moment of each review, so
 *   the two lines can be compared: measured below predicted means the model
 *   is optimistic, above means it is pessimistic.
 *
 * Pure: everything it needs comes in as values, so it is computed once per
 * change in the data and can be tested without a database.
 */
data class RetentionReport(
    /** Every review and every in-use learning state, across all contexts. */
    val overall: RetentionSummary,
    /** Global first, then each learning group by name. */
    val learningContexts: List<LearningContextReport>,
    /** Each card group, measured against the context it learns in. */
    val cardGroups: List<CardGroupReport>,
    val period: RetentionPeriod
)

enum class RetentionPeriod { WEEK, MONTH }

data class RetentionSummary(
    /** Enabled cards in scope. */
    val cardCount: Int,
    /** Of those, how many have been reviewed at least once in this scope. */
    val learnedCount: Int,
    /** Learned cards whose next review is already due. */
    val dueCount: Int,
    /** Mean probability of recall right now over learned cards; null when none are learned. */
    val predictedRetentionNow: Double?,
    /** Mean stability in days over learned cards; null when none are learned. */
    val averageStabilityDays: Double?,
    /** Reviews that count towards measured recall (seen-before, not skipped). */
    val reviews: Int,
    /** Of [reviews], those not graded Again. */
    val recalled: Int,
    /** Measured recall over the last 30 days; null when there were no reviews. */
    val recentRecall: Double?,
    /** One point per period that had at least one counted review, oldest first. */
    val trend: List<RetentionPoint>
) {
    /** All-time measured recall; null when there were no reviews. */
    val measuredRecall: Double? get() = if (reviews == 0) null else recalled.toDouble() / reviews
}

data class RetentionPoint(
    /** Epoch millis of the start of the period, local time. */
    val periodStart: Long,
    val reviews: Int,
    val recalled: Int,
    /** Mean FSRS-predicted recall at review time; null when no review carried a stability. */
    val predicted: Double?
) {
    val recall: Double get() = recalled.toDouble() / reviews
}

data class LearningContextReport(
    /** null is Global. */
    val learningGroupId: Long?,
    val name: String,
    /** Names of the card groups that learn in this context. */
    val cardGroupNames: List<String>,
    val summary: RetentionSummary,
    /** Every enabled card in this context, weakest predicted retention first. */
    val cards: List<CardRetention>
)

data class CardGroupReport(
    val groupId: Long,
    val name: String,
    /** null is Global. */
    val learningGroupId: Long?,
    val learningContextName: String,
    val summary: RetentionSummary,
    /** The group's enabled cards, in the state of the context it learns in, weakest first. */
    val cards: List<CardRetention>
)

data class CardRetention(
    val cardId: Long,
    val question: String,
    /** NEW, LEARNING, REVIEW or RELEARNING, in this context. */
    val state: String,
    val stabilityDays: Double,
    val difficulty: Double,
    /** Probability of recall right now; null for a card not yet learned in this context. */
    val retrievabilityNow: Double?,
    val reps: Int,
    val lapses: Int,
    val lastReview: Long,
    val nextReview: Long,
    val reviews: Int,
    val recalled: Int,
    /** The card's counted reviews in this context, oldest first. */
    val history: List<CardReviewPoint>
) {
    val measuredRecall: Double? get() = if (reviews == 0) null else recalled.toDouble() / reviews
    val isLearned: Boolean get() = state != STATE_NEW && lastReview > 0L
}

data class CardReviewPoint(
    val reviewTime: Long,
    val grade: Int,
    /** FSRS-predicted recall just before this review; null when unknown. */
    val predicted: Double?
)

private const val STATE_NEW = "NEW"
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val RECENT_WINDOW_DAYS = 30

object RetentionReportBuilder {

    const val GLOBAL_NAME = "Global"

    /**
     * @param retrievability probability of recall after a number of days at a
     *   given stability; FSRS's forgetting curve unless a test supplies another.
     */
    fun build(
        cards: List<Card>,
        groups: List<Group>,
        crossRefs: List<CardGroupCrossRef>,
        learningGroups: List<LearningGroup>,
        learningStates: List<CardLearningState>,
        logs: List<ReviewLog>,
        now: Long,
        utcOffsetSeconds: Int,
        period: RetentionPeriod = RetentionPeriod.WEEK,
        retrievability: (elapsedDays: Double, stability: Double) -> Double =
            FSRSAlgorithm()::retrievability
    ): RetentionReport {
        val enabledCards = cards.filter { !it.isDisabled }
        val cardsById = cards.associateBy { it.id }
        val groupsById = groups.associateBy { it.id }
        val groupsByName = groups.associateBy { it.name }
        val learningGroupIds = learningGroups.map { it.id }.toSet()
        val statesByKey = learningStates.associateBy { it.cardId to it.learningGroupId }

        // A group pointed at a learning group that no longer exists learns Global.
        fun contextOf(group: Group): Long? = group.learningGroupId?.takeIf { it in learningGroupIds }

        val cardIdsByGroup: Map<Long, Set<Long>> = crossRefs
            .groupBy({ it.groupId }, { it.cardId })
            .mapValues { it.value.toSet() }

        // Only reviews of a card that had been seen before measure recall:
        // a first sighting has nothing to remember, and a skip is not a grade.
        val countedLogs = logs.filter { it.grade in 1..4 && parseState(it.stateBefore) != STATE_NEW }

        // Which context a log graded. A log keeps only the name of the group it
        // was practised in, so this follows the group as it is today: renaming a
        // group or moving it between learning groups moves its history with it.
        // Practice across all cards, and grades from the card editor, are Global.
        // A group that has since been deleted is attributed nowhere but overall.
        val unattributed = Any()
        fun logContext(log: ReviewLog): Any? {
            val name = log.groupName
            if (name.isNullOrBlank() || name == GROUP_NAME_CARD_EDIT) return null
            val group = groupsByName[name] ?: return unattributed
            return contextOf(group)
        }
        val logsByContext: Map<Any?, List<ReviewLog>> = countedLogs.groupBy(::logContext)
        val logsByGroupName = countedLogs.groupBy { it.groupName }

        fun snapshotOf(card: Card, learningGroupId: Long?): Snapshot {
            if (learningGroupId == null) {
                return Snapshot(
                    card.fsrsState, card.fsrsStability, card.fsrsDifficulty,
                    card.fsrsReps, card.fsrsLapses, card.lastReview, card.nextReview
                )
            }
            val state = statesByKey[card.id to learningGroupId]
                ?: return Snapshot(STATE_NEW, 0.0, 0.0, 0, 0, 0L, 0L)
            return Snapshot(
                state.fsrsState, state.fsrsStability, state.fsrsDifficulty,
                state.fsrsReps, state.fsrsLapses, state.lastReview, state.nextReview
            )
        }

        fun retrievabilityNow(s: Snapshot): Double? {
            if (s.state == STATE_NEW || s.lastReview <= 0L || s.stability <= 0.0) return null
            val elapsed = (now - s.lastReview).toDouble() / DAY_MILLIS
            return retrievability(elapsed, s.stability)
        }

        fun predictedAtReview(log: ReviewLog): Double? {
            val stability = parseStability(log.stateBefore) ?: return null
            if (stability <= 0.0) return null
            return retrievability(log.elapsedDays.toDouble(), stability)
        }

        fun summarize(snapshots: List<Snapshot>, scopeLogs: List<ReviewLog>): RetentionSummary {
            val learned = snapshots.filter { it.state != STATE_NEW && it.lastReview > 0L }
            val rNow = learned.mapNotNull(::retrievabilityNow)
            val recentSince = now - RECENT_WINDOW_DAYS * DAY_MILLIS
            val recent = scopeLogs.filter { it.reviewTime >= recentSince }
            val trend = scopeLogs
                .groupBy { periodStart(it.reviewTime, utcOffsetSeconds, period) }
                .entries
                .sortedBy { it.key }
                .map { (start, inPeriod) ->
                    val predicted = inPeriod.mapNotNull(::predictedAtReview)
                    RetentionPoint(
                        periodStart = start,
                        reviews = inPeriod.size,
                        recalled = inPeriod.count { it.grade >= 2 },
                        predicted = predicted.takeIf { it.isNotEmpty() }?.average()
                    )
                }
            return RetentionSummary(
                cardCount = snapshots.size,
                learnedCount = learned.size,
                dueCount = learned.count { it.nextReview in 1..now },
                predictedRetentionNow = rNow.takeIf { it.isNotEmpty() }?.average(),
                averageStabilityDays = learned.takeIf { it.isNotEmpty() }?.map { it.stability }?.average(),
                reviews = scopeLogs.size,
                recalled = scopeLogs.count { it.grade >= 2 },
                recentRecall = recent.takeIf { it.isNotEmpty() }
                    ?.let { r -> r.count { it.grade >= 2 }.toDouble() / r.size },
                trend = trend
            )
        }

        // --- Learning contexts -------------------------------------------------

        val contextIds: List<Long?> = listOf<Long?>(null) +
            learningGroups.sortedBy { it.name.lowercase() }.map { it.id }

        val contextReports = contextIds.map { lgId ->
            val memberGroups = groups.filter { contextOf(it) == lgId }
            // Every card keeps a Global state, so Global covers the whole
            // collection; a learning group covers the cards in its groups.
            val contextCards = if (lgId == null) enabledCards else {
                val ids = memberGroups.flatMap { cardIdsByGroup[it.id].orEmpty() }.toSet()
                enabledCards.filter { it.id in ids }
            }
            val contextLogs = logsByContext[lgId].orEmpty()
            val logsByCard = contextLogs.groupBy { it.cardId }

            val cardRows = contextCards.map { card ->
                val s = snapshotOf(card, lgId)
                val cardLogs = logsByCard[card.id].orEmpty().sortedBy { it.reviewTime }
                CardRetention(
                    cardId = card.id,
                    question = card.question,
                    state = s.state,
                    stabilityDays = s.stability,
                    difficulty = s.difficulty,
                    retrievabilityNow = retrievabilityNow(s),
                    reps = s.reps,
                    lapses = s.lapses,
                    lastReview = s.lastReview,
                    nextReview = s.nextReview,
                    reviews = cardLogs.size,
                    recalled = cardLogs.count { it.grade >= 2 },
                    history = cardLogs.map { CardReviewPoint(it.reviewTime, it.grade, predictedAtReview(it)) }
                )
            }.sortedWith(
                // Weakest first; cards not yet learned at the bottom.
                compareBy<CardRetention> { it.retrievabilityNow == null }
                    .thenBy { it.retrievabilityNow ?: 0.0 }
                    .thenBy { it.question.lowercase() }
            )

            LearningContextReport(
                learningGroupId = lgId,
                name = lgId?.let { id -> learningGroups.first { it.id == id }.name } ?: GLOBAL_NAME,
                cardGroupNames = memberGroups.map { it.name }.sorted(),
                summary = summarize(contextCards.map { snapshotOf(it, lgId) }, contextLogs),
                cards = cardRows
            )
        }

        val contextNames = contextReports.associate { it.learningGroupId to it.name }
        val contextCards = contextReports.associate { it.learningGroupId to it.cards }

        // --- Card groups --------------------------------------------------------

        val groupReports = groups.sortedBy { it.name.lowercase() }.map { group ->
            val lgId = contextOf(group)
            val ids = cardIdsByGroup[group.id].orEmpty()
            val groupCards = enabledCards.filter { it.id in ids }
            CardGroupReport(
                groupId = group.id,
                name = group.name,
                learningGroupId = lgId,
                learningContextName = contextNames[lgId] ?: GLOBAL_NAME,
                summary = summarize(
                    groupCards.map { snapshotOf(it, lgId) },
                    logsByGroupName[group.name].orEmpty()
                ),
                cards = contextCards[lgId].orEmpty().filter { it.cardId in ids }
            )
        }

        // --- Overall ------------------------------------------------------------

        // Each (card, context) pair in use counts once: the Global state of
        // every card, plus its state in each learning group it belongs to.
        val overallSnapshots = contextReports.flatMap { ctx ->
            ctx.cards.mapNotNull { row -> cardsById[row.cardId]?.let { snapshotOf(it, ctx.learningGroupId) } }
        }
        val overall = summarize(overallSnapshots, countedLogs)

        return RetentionReport(
            overall = overall,
            learningContexts = contextReports,
            cardGroups = groupReports,
            period = period
        )
    }

    private data class Snapshot(
        val state: String,
        val stability: Double,
        val difficulty: Double,
        val reps: Int,
        val lapses: Int,
        val lastReview: Long,
        val nextReview: Long
    )

    /** The `ST:` field of a serialized review state, e.g. `S:3.1,D:5.2,ST:REVIEW`. */
    internal fun parseState(serialized: String): String? = field(serialized, "ST")

    /** The `S:` (stability) field of a serialized review state. */
    internal fun parseStability(serialized: String): Double? = field(serialized, "S")?.toDoubleOrNull()

    private fun field(serialized: String, key: String): String? =
        serialized.split(',').firstNotNullOfOrNull { part ->
            val i = part.indexOf(':')
            if (i > 0 && part.substring(0, i).trim() == key) part.substring(i + 1).trim() else null
        }

    /**
     * Start of the local-time week (Monday) or calendar month containing
     * [epochMillis], as epoch millis.
     */
    internal fun periodStart(epochMillis: Long, utcOffsetSeconds: Int, period: RetentionPeriod): Long {
        val offsetMillis = utcOffsetSeconds * 1000L
        val localDay = (epochMillis + offsetMillis).floorDiv(DAY_MILLIS)
        val startDay = when (period) {
            // 1970-01-01 was a Thursday, so Monday-aligned weeks are offset by 3 days.
            RetentionPeriod.WEEK -> (localDay + 3).floorDiv(7L) * 7 - 3
            RetentionPeriod.MONTH -> localDay - (dayOfMonth(localDay) - 1)
        }
        return startDay * DAY_MILLIS - offsetMillis
    }

    /** Day of the month (1-31) for a count of days since 1970-01-01; Howard Hinnant's civil_from_days. */
    private fun dayOfMonth(days: Long): Int {
        val z = days + 719468
        val era = z.floorDiv(146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        return (doy - (153 * mp + 2) / 5 + 1).toInt()
    }
}
