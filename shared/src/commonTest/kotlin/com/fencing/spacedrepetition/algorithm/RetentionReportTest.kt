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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetentionReportTest {

    private val day = 24L * 60 * 60 * 1000
    // Monday 2026-11-09 00:00 UTC
    private val monday = 1_794_182_400_000L
    private val now = monday + 6 * day

    private fun card(id: Long, state: String = "REVIEW", stability: Double = 10.0, lastReview: Long = now - day) =
        Card(
            id = id, question = "Q$id", answer = "A$id",
            fsrsState = state, fsrsStability = stability,
            lastReview = if (state == "NEW") 0L else lastReview,
            nextReview = if (state == "NEW") 0L else lastReview + 10 * day
        )

    private fun log(
        cardId: Long, grade: Int, time: Long, group: String? = null,
        before: String = "REVIEW", stability: Double = 10.0, elapsed: Int = 5, after: Double? = null
    ) = ReviewLog(
        cardId = cardId, sessionId = null, reviewTime = time, grade = grade, algorithm = "FSRS",
        stateBefore = "S:$stability,D:5.0,ST:$before",
        stateAfter = after?.let { "S:$it,D:5.0,ST:REVIEW" } ?: "", scheduledDays = 0,
        elapsedDays = elapsed, groupName = group
    )

    private val lg = LearningGroup(id = 7, name = "Sabre")
    private val groups = listOf(
        Group(id = 1, name = "Parries"),
        Group(id = 2, name = "Cuts", learningGroupId = 7)
    )
    private val refs = listOf(
        CardGroupCrossRef(1, 1),
        CardGroupCrossRef(2, 2),
        CardGroupCrossRef(3, 2)
    )

    private fun build(
        cards: List<Card> = listOf(card(1), card(2), card(3, state = "NEW")),
        states: List<CardLearningState> = listOf(
            CardLearningState(2, 7, fsrsStability = 4.0, fsrsState = "REVIEW", lastReview = now - 2 * day, nextReview = now - day)
        ),
        logs: List<ReviewLog> = emptyList(),
        retrievability: (Double, Double) -> Double = { t, s -> 1.0 / (1.0 + t / s) }
    ) = RetentionReportBuilder.build(
        cards, groups, refs, listOf(lg), states, logs,
        now = now, utcOffsetSeconds = 0, retrievability = retrievability
    )

    @Test
    fun `global covers every card and learning groups cover their groups' cards`() {
        val report = build()
        val (global, sabre) = report.learningContexts
        assertEquals("Global", global.name)
        assertEquals(3, global.summary.cardCount)
        assertEquals(2, global.summary.learnedCount)
        assertEquals("Sabre", sabre.name)
        assertEquals(listOf("Cuts"), sabre.cardGroupNames)
        assertEquals(setOf(2L, 3L), sabre.cards.map { it.cardId }.toSet())
        // Card 3 has no state in Sabre, so it is new there.
        assertEquals(1, sabre.summary.learnedCount)
        assertEquals(1, sabre.summary.dueCount)
    }

    @Test
    fun `predicted retention now uses the state of each context`() {
        val report = build()
        val sabre = report.learningContexts[1]
        val card2 = sabre.cards.first { it.cardId == 2L }
        // 2 days at stability 4 under the test curve: 1 / (1 + 0.5)
        assertTrue(abs(card2.retrievabilityNow!! - 1.0 / 1.5) < 1e-9)
        assertNull(sabre.cards.first { it.cardId == 3L }.retrievabilityNow)
        // Weakest first, unlearned last.
        assertEquals(3L, sabre.cards.last().cardId)
    }

    @Test
    fun `measured recall ignores first sightings and skips and attributes logs by group`() {
        val logs = listOf(
            log(1, 3, monday, group = "Parries"),
            log(1, 1, monday + day, group = null),
            log(1, 4, monday + day, group = GROUP_NAME_CARD_EDIT),
            log(2, 3, monday, group = "Cuts"),
            log(2, 1, monday + 2 * day, group = "Cuts"),
            log(2, 3, monday, group = "Cuts", before = "NEW"),   // first sighting
            log(2, 0, monday, group = "Cuts"),                   // skip
            log(3, 1, monday, group = "Deleted group")
        )
        val report = build(logs = logs)
        val (global, sabre) = report.learningContexts

        assertEquals(3, global.summary.reviews)
        assertEquals(2, global.summary.recalled)
        assertEquals(2, sabre.summary.reviews)
        assertEquals(1, sabre.summary.recalled)
        // Overall counts the deleted group's review too.
        assertEquals(6, report.overall.reviews)

        val cuts = report.cardGroups.first { it.name == "Cuts" }
        assertEquals("Sabre", cuts.learningContextName)
        assertEquals(2, cuts.summary.reviews)
        assertEquals(setOf(2L, 3L), cuts.cards.map { it.cardId }.toSet())
        assertEquals(listOf(1L), report.cardGroups.first { it.name == "Parries" }.cards.map { it.cardId })
        val parries = report.cardGroups.first { it.name == "Parries" }
        assertEquals(1, parries.summary.reviews)
        assertEquals(1.0, parries.summary.measuredRecall)

        val card2 = sabre.cards.first { it.cardId == 2L }
        assertEquals(listOf(3, 1), card2.history.map { it.grade })
    }

    @Test
    fun `trend buckets reviews by week and records predicted recall at review time`() {
        val logs = listOf(
            log(1, 3, monday - 3 * day, stability = 5.0, elapsed = 5),
            log(1, 1, monday + day, stability = 10.0, elapsed = 10),
            log(1, 3, monday + 2 * day, stability = 10.0, elapsed = 0)
        )
        val trend = build(logs = logs).learningContexts[0].summary.trend
        assertEquals(listOf(monday - 7 * day, monday), trend.map { it.periodStart })
        assertEquals(1, trend[0].reviews)
        assertEquals(0.5, trend[0].predicted)
        assertEquals(2, trend[1].reviews)
        assertEquals(0.5, trend[1].recall)
        assertEquals((0.5 + 1.0) / 2, trend[1].predicted)
    }

    @Test
    fun `period start aligns to Monday and to the first of the month in local time`() {
        val wednesday = monday + 2 * day + 15 * 60 * 60 * 1000
        assertEquals(monday, RetentionReportBuilder.periodStart(wednesday, 0, RetentionPeriod.WEEK))
        // 2026-11-01 00:00 UTC
        val novFirst = monday - 8 * day
        assertEquals(novFirst, RetentionReportBuilder.periodStart(wednesday, 0, RetentionPeriod.MONTH))
        // One hour east of UTC: local midnight is an hour earlier in UTC.
        assertEquals(monday - 3_600_000, RetentionReportBuilder.periodStart(wednesday, 3600, RetentionPeriod.WEEK))
    }

    @Test
    fun `disabled cards are left out of the snapshot`() {
        val report = build(cards = listOf(card(1), card(2).copy(isDisabled = true), card(3, state = "NEW")))
        assertEquals(2, report.learningContexts[0].summary.cardCount)
    }

    @Test
    fun `parses the serialized review state`() {
        assertEquals("RELEARNING", RetentionReportBuilder.parseState("S:1.5,D:4.0,ST:RELEARNING"))
        assertEquals(1.5, RetentionReportBuilder.parseStability("S:1.5,D:4.0,ST:RELEARNING"))
        assertNull(RetentionReportBuilder.parseStability("garbage"))
    }

    @Test
    fun `FSRS retrievability is 90 percent at one stability`() {
        val r = FSRSAlgorithm().retrievability(12.0, 12.0)
        assertTrue(abs(r - 0.9) < 1e-9)
        assertEquals(1.0, FSRSAlgorithm().retrievability(0.0, 12.0))
    }

    @Test
    fun `wilson interval brackets the rate and stays inside zero to one`() {
        val all = wilsonInterval(10, 10)!!
        assertEquals(1.0, all.endInclusive)
        assertTrue(all.start in 0.65..0.75)   // ~0.72 for 10/10
        val half = wilsonInterval(50, 100)!!
        assertTrue(abs(half.start - 0.404) < 0.002 && abs(half.endInclusive - 0.596) < 0.002)
        assertNull(wilsonInterval(0, 0))
        val point = RetentionPoint(monday, reviews = 4, recalled = 3, predicted = null)
        assertTrue(0.75 in point.recallInterval)
    }

    @Test
    fun `quantiles interpolate between neighbours`() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(2.5, RetentionReportBuilder.quantile(xs, 0.5))
        assertEquals(1.75, RetentionReportBuilder.quantile(xs, 0.25))
        assertEquals(7.0, RetentionReportBuilder.quantile(listOf(7.0), 0.75))
    }

    @Test
    fun `stability trend carries each card's latest stability forward`() {
        val logs = listOf(
            // First sighting counts for stability though not for recall.
            log(1, 3, monday - 6 * day, before = "NEW", after = 2.0),
            log(2, 3, monday - 5 * day, before = "NEW", after = 4.0),
            log(1, 3, monday + day, after = 8.0),
            log(1, 0, monday + 2 * day, after = 99.0)   // skip: ignored
        )
        val ctx = build(logs = logs).learningContexts[0]
        val trend = ctx.summary.stabilityTrend
        assertEquals(listOf(monday - 7 * day, monday), trend.map { it.periodStart })
        assertEquals(3.0, trend[0].median)
        assertEquals(2, trend[1].cards)
        assertEquals(6.0, trend[1].median)   // cards at 8 and 4
        assertEquals(1, ctx.summary.reviews)
        val card1 = ctx.cards.first { it.cardId == 1L }
        assertEquals(listOf(2.0, 8.0), card1.stabilityHistory.map { it.stability })
    }
}
