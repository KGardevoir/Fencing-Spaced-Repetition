// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.ReviewLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConceptSchedulingTest {

    // ==================== combineConceptGrades ====================

    @Test
    fun `a single grade passes straight through`() {
        Grade.entries.filter { it != Grade.SKIP }.forEach {
            assertEquals(it, ConceptScheduling.combineConceptGrades(listOf(it)))
        }
    }

    @Test
    fun `several grades average to the nearest grade, half up`() {
        assertEquals(Grade.HARD, ConceptScheduling.combineConceptGrades(listOf(Grade.AGAIN, Grade.GOOD)))
        assertEquals(Grade.GOOD, ConceptScheduling.combineConceptGrades(listOf(Grade.HARD, Grade.GOOD)))
        assertEquals(Grade.GOOD, ConceptScheduling.combineConceptGrades(listOf(Grade.AGAIN, Grade.EASY, Grade.EASY)))
        assertEquals(Grade.HARD, ConceptScheduling.combineConceptGrades(listOf(Grade.AGAIN, Grade.AGAIN, Grade.EASY)))
    }

    @Test
    fun `skips and empty lists are refused`() {
        assertFailsWith<IllegalArgumentException> { ConceptScheduling.combineConceptGrades(emptyList()) }
        assertFailsWith<IllegalArgumentException> { ConceptScheduling.combineConceptGrades(listOf(Grade.SKIP)) }
    }

    // ==================== greedyDotProduct ====================

    private fun draw(vectors: Map<Long, Map<String, Double>>, need: Map<String, Double>, limit: Int = 10) =
        ConceptScheduling.greedyDotProduct(vectors.keys.sorted(), vectors, need, limit)

    @Test
    fun `the card meeting the most need is taken first`() {
        val picks = draw(
            mapOf(1L to mapOf("A" to 1.0), 2L to mapOf("B" to 1.0)),
            mapOf("A" to 0.2, "B" to 0.7)
        )
        assertEquals(listOf(2L, 1L), picks.map { it.cardId })
        assertEquals(0.7, picks.first().score, 1e-9)
    }

    @Test
    fun `a score is the dot product of shares and need`() {
        // 2 parts A to 1 part B: shares 2/3 and 1/3.
        val picks = draw(mapOf(1L to mapOf("A" to 2.0 / 3, "B" to 1.0 / 3)), mapOf("A" to 0.6, "B" to 0.3), limit = 1)
        assertEquals(2.0 / 3 * 0.6 + 1.0 / 3 * 0.3, picks.single().score, 1e-9)
        assertEquals(mapOf("A" to 0.4, "B" to 0.1), picks.single().terms.mapValues { (_, v) -> kotlin.math.round(v * 1e9) / 1e9 })
    }

    @Test
    fun `a card covers only its share of a concept's need`() {
        // Card 1 is half A: it leaves half of A's need, which card 2 then meets.
        val picks = draw(
            mapOf(1L to mapOf("A" to 0.5, "B" to 0.5), 2L to mapOf("A" to 1.0), 3L to mapOf("C" to 1.0)),
            mapOf("A" to 1.0, "B" to 1.0, "C" to 0.4)
        )
        assertEquals(listOf(1L, 2L, 3L), picks.map { it.cardId })
        assertEquals(0.5, picks[1].score, 1e-9)
    }

    @Test
    fun `nothing is taken once no need is left, and ties go to the first in order`() {
        val picks = draw(mapOf(5L to mapOf("A" to 1.0), 3L to mapOf("A" to 1.0)), mapOf("A" to 1.0))
        assertEquals(listOf(3L), picks.map { it.cardId })   // 3 first in id order; then A has no need left
        assertEquals(emptyList(), draw(mapOf(1L to mapOf("A" to 1.0)), mapOf("A" to 0.0)))
    }

    @Test
    fun `staleness decides between cards meeting much the same need`() {
        // Card 1 meets a little more need, but was graded just now; card 2 has rested.
        val vectors = mapOf(1L to mapOf("A" to 1.0), 2L to mapOf("B" to 1.0))
        val need = mapOf("A" to 0.6, "B" to 0.5)
        val fresh = ConceptScheduling.stalenessFactor(0.0)
        val rested = ConceptScheduling.stalenessFactor(30.0)
        val picks = ConceptScheduling.greedyDotProduct(listOf(1L, 2L), vectors, need, 1) { if (it == 1L) fresh else rested }
        assertEquals(2L, picks.single().cardId)
        // What it covers is still its plain share of the need.
        assertEquals(0.5, picks.single().score, 1e-9)
        // But staleness never makes a card meet need it does not have.
        assertEquals(emptyList(), ConceptScheduling.greedyDotProduct(listOf(1L), vectors, mapOf("A" to 0.0), 1) { 1.0 })
    }

    @Test
    fun `staleness runs from a half just after grading to one`() {
        assertEquals(0.5, ConceptScheduling.stalenessFactor(0.0), 1e-9)
        assertEquals(1.0, ConceptScheduling.stalenessFactor(null), 1e-9)
        assertEquals(0.5 + 0.5 * (1 - kotlin.math.exp(-1.0)), ConceptScheduling.stalenessFactor(7.0), 1e-9)
        assertTrue(ConceptScheduling.stalenessFactor(21.0) > 0.97)
    }

    // ==================== countDue ====================

    private fun c(id: Long, next: Long) =
        com.fencing.spacedrepetition.data.model.Card(id = id, question = "Q$id", answer = "", nextReview = next)
    private val noRecall = { _: Double, _: Double -> 0.0 }

    @Test
    fun `card by card, cards are counted by their own due dates`() {
        val cards = listOf(c(1, next = 0), c(2, next = 500))
        assertEquals(1, ConceptScheduling.countDue(cards, null, false, emptyMap(), emptyMap(), emptyMap(), 100, noRecall))
        // In a learning group, a card's state there; one without goes by Global.
        val states = mapOf((1L to 7L) to com.fencing.spacedrepetition.data.model.CardLearningState(1, 7, nextReview = 900))
        assertEquals(0, ConceptScheduling.countDue(cards, 7L, false, states, emptyMap(), emptyMap(), 100, noRecall))
    }

    @Test
    fun `by concept, the count is the cards it takes to meet the need`() {
        // One new concept on five cards: one card meets all of it.
        val five = (1L..5L).map { c(it, next = 0) }
        val shares = (1L..5L).associate { (it to 10L) to 1.0 }
        assertEquals(1, ConceptScheduling.countDue(five, null, true, emptyMap(), shares, emptyMap(), 100, noRecall))
        // Two cards each half of two concepts: both are needed.
        val halves = mapOf((1L to 10L) to 0.5, (1L to 11L) to 0.5, (2L to 10L) to 0.5, (2L to 11L) to 0.5)
        assertEquals(2, ConceptScheduling.countDue(five.take(2), null, true, emptyMap(), halves, emptyMap(), 100, noRecall))
        // A concept not due yet needs nothing; an untagged card due by its own date still counts.
        val later = mapOf(10L to com.fencing.spacedrepetition.data.model.ConceptLearningState(10, 0, nextReview = 500))
        val mixed = listOf(c(1, next = 0), c(6, next = 0))
        assertEquals(1, ConceptScheduling.countDue(mixed, null, true, emptyMap(), mapOf((1L to 10L) to 1.0), later, 100, noRecall))
    }

    // ==================== shuffleByBucket ====================

    @Test
    fun `bucket shuffling keeps buckets in order`() {
        val hour = 60 * 60 * 1000L
        val items = listOf(5 * hour, 0L, 1L, 3 * hour, 2L)
        val shuffled = CardRepository.shuffleByBucket(items, bucketHours = 2) { it }
        assertEquals(setOf(0L, 1L, 2L), shuffled.take(3).toSet())
        assertEquals(listOf(3 * hour, 5 * hour), shuffled.drop(3))
    }

    // ==================== conceptReviewEvents ====================

    private fun log(id: Long, grade: Grade, time: Long, session: Long? = null, group: String? = null) =
        ReviewLog(
            id = id, cardId = id, sessionId = session, reviewTime = time, grade = grade.value,
            algorithm = "FSRS", stateBefore = "", stateAfter = "", scheduledDays = 0, elapsedDays = 0,
            groupName = group, stabilityMultiplier = 1.0 + id
        )

    @Test
    fun `a session's logs make one review at its first time, with the mean grade`() {
        val events = ConceptScheduling.conceptReviewEvents(
            listOf(
                log(2, Grade.AGAIN, 20, session = 7, group = "Footwork"),
                log(1, Grade.GOOD, 10, session = 7, group = "Footwork"),
                log(3, Grade.SKIP, 30, session = 7, group = "Footwork")
            )
        )
        assertEquals(1, events.size)
        assertEquals(10L, events.single().time)
        assertEquals(Grade.HARD, events.single().grade)
        assertEquals("Footwork", events.single().groupName)
        assertEquals(2.0, events.single().stabilityMultiplier)
    }

    @Test
    fun `logs outside a session are reviews of their own, in time order`() {
        val events = ConceptScheduling.conceptReviewEvents(
            listOf(log(1, Grade.EASY, 50), log(2, Grade.AGAIN, 5), log(3, Grade.GOOD, 20, session = 1))
        )
        assertEquals(listOf(5L, 20L, 50L), events.map { it.time })
        assertEquals(listOf(Grade.AGAIN, Grade.GOOD, Grade.EASY), events.map { it.grade })
    }

    @Test
    fun `a session of nothing but skips is no review`() {
        assertEquals(
            emptyList(),
            ConceptScheduling.conceptReviewEvents(listOf(log(1, Grade.SKIP, 1, session = 3), log(2, Grade.SKIP, 2, session = 3)))
        )
    }

    // ==================== weights ====================

    @Test
    fun `weighted grades lean towards the cards with more of the concept`() {
        assertEquals(Grade.EASY, ConceptScheduling.combineWeightedConceptGrades(listOf(Grade.EASY to 1.0, Grade.AGAIN to 0.2)))
        assertEquals(Grade.AGAIN, ConceptScheduling.combineWeightedConceptGrades(listOf(Grade.EASY to 0.1, Grade.AGAIN to 1.0)))
        // Equal weights are the plain mean, whatever the weight.
        assertEquals(
            ConceptScheduling.combineConceptGrades(listOf(Grade.HARD, Grade.GOOD)),
            ConceptScheduling.combineWeightedConceptGrades(listOf(Grade.HARD to 0.5, Grade.GOOD to 0.5))
        )
        assertFailsWith<IllegalArgumentException> { ConceptScheduling.combineWeightedConceptGrades(listOf(Grade.GOOD to 0.0)) }
    }

    @Test
    fun `a card that contains none of a concept says nothing about it`() {
        val events = ConceptScheduling.conceptReviewEvents(
            listOf(log(1, Grade.EASY, 10, session = 1), log(2, Grade.AGAIN, 11, session = 1))
        ) { cardId -> if (cardId == 2L) 0.0 else 1.0 }
        assertEquals(Grade.EASY, events.single().grade)
    }

    @Test
    fun `weights are proportions within a card`() {
        val shares = com.fencing.spacedrepetition.data.model.conceptShares(
            listOf(
                com.fencing.spacedrepetition.data.model.CardConceptCrossRef(1, 10, weight = 2.0),
                com.fencing.spacedrepetition.data.model.CardConceptCrossRef(1, 11, weight = 1.0),
                com.fencing.spacedrepetition.data.model.CardConceptCrossRef(2, 10, weight = 7.0),
                com.fencing.spacedrepetition.data.model.CardConceptCrossRef(3, 10, weight = 0.0)
            )
        )
        assertEquals(2.0 / 3, shares.getValue(1L to 10L), 1e-9)
        assertEquals(1.0 / 3, shares.getValue(1L to 11L), 1e-9)
        assertEquals(1.0, shares.getValue(2L to 10L))   // one concept: all of it, whatever the number
        assertEquals(0.0, shares.getValue(3L to 10L))   // all zero: none
        assertEquals(1.0, com.fencing.spacedrepetition.data.model.CardConceptCrossRef.sanitize(Double.NaN))
        assertEquals(0.0, com.fencing.spacedrepetition.data.model.CardConceptCrossRef.sanitize(-3.0))
        assertEquals(12.5, com.fencing.spacedrepetition.data.model.CardConceptCrossRef.sanitize(12.5))
    }
}
