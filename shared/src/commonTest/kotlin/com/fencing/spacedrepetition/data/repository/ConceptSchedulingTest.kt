// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.ReviewLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConceptSchedulingTest {

    private fun card(id: Long) = Card(id = id, question = "Q$id", answer = "")

    // ==================== combineConceptGrades ====================

    @Test
    fun `a single grade passes straight through`() {
        Grade.entries.filter { it != Grade.SKIP }.forEach {
            assertEquals(it, CardRepository.combineConceptGrades(listOf(it)))
        }
    }

    @Test
    fun `several grades average to the nearest grade, half up`() {
        assertEquals(Grade.HARD, CardRepository.combineConceptGrades(listOf(Grade.AGAIN, Grade.GOOD)))
        assertEquals(Grade.GOOD, CardRepository.combineConceptGrades(listOf(Grade.HARD, Grade.GOOD)))
        assertEquals(Grade.GOOD, CardRepository.combineConceptGrades(listOf(Grade.AGAIN, Grade.EASY, Grade.EASY)))
        assertEquals(Grade.HARD, CardRepository.combineConceptGrades(listOf(Grade.AGAIN, Grade.AGAIN, Grade.EASY)))
    }

    @Test
    fun `skips and empty lists are refused`() {
        assertFailsWith<IllegalArgumentException> { CardRepository.combineConceptGrades(emptyList()) }
        assertFailsWith<IllegalArgumentException> { CardRepository.combineConceptGrades(listOf(Grade.SKIP)) }
    }

    // ==================== pickBestCoveringCard ====================

    @Test
    fun `picks the card covering the most uncovered concepts`() {
        val concepts = mapOf(1L to setOf(10L), 2L to setOf(10L, 11L, 12L), 3L to setOf(10L, 11L))
        val best = CardRepository.pickBestCoveringCard(
            listOf(card(1), card(2), card(3)), concepts, uncovered = setOf(10L, 11L, 12L), randomize = false
        )
        assertEquals(2L, best.id)
    }

    @Test
    fun `ties go to the card with fewer concepts that are not due`() {
        val concepts = mapOf(1L to setOf(10L, 99L, 98L), 2L to setOf(10L, 99L), 3L to setOf(10L, 97L, 96L))
        val best = CardRepository.pickBestCoveringCard(
            listOf(card(1), card(2), card(3)), concepts, uncovered = setOf(10L), randomize = false
        )
        assertEquals(2L, best.id)
    }

    @Test
    fun `a full tie goes to the lowest id when not randomising`() {
        val concepts = mapOf(4L to setOf(10L), 2L to setOf(10L), 3L to setOf(10L))
        val best = CardRepository.pickBestCoveringCard(
            listOf(card(4), card(3), card(2)), concepts, uncovered = setOf(10L), randomize = false
        )
        assertEquals(2L, best.id)
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
        val events = CardRepository.conceptReviewEvents(
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
        val events = CardRepository.conceptReviewEvents(
            listOf(log(1, Grade.EASY, 50), log(2, Grade.AGAIN, 5), log(3, Grade.GOOD, 20, session = 1))
        )
        assertEquals(listOf(5L, 20L, 50L), events.map { it.time })
        assertEquals(listOf(Grade.AGAIN, Grade.GOOD, Grade.EASY), events.map { it.grade })
    }

    @Test
    fun `a session of nothing but skips is no review`() {
        assertEquals(
            emptyList(),
            CardRepository.conceptReviewEvents(listOf(log(1, Grade.SKIP, 1, session = 3), log(2, Grade.SKIP, 2, session = 3)))
        )
    }

    // ==================== weights ====================

    @Test
    fun `weighted grades lean towards the cards with more of the concept`() {
        assertEquals(Grade.EASY, CardRepository.combineWeightedConceptGrades(listOf(Grade.EASY to 1.0, Grade.AGAIN to 0.2)))
        assertEquals(Grade.AGAIN, CardRepository.combineWeightedConceptGrades(listOf(Grade.EASY to 0.1, Grade.AGAIN to 1.0)))
        // Equal weights are the plain mean, whatever the weight.
        assertEquals(
            CardRepository.combineConceptGrades(listOf(Grade.HARD, Grade.GOOD)),
            CardRepository.combineWeightedConceptGrades(listOf(Grade.HARD to 0.5, Grade.GOOD to 0.5))
        )
        assertFailsWith<IllegalArgumentException> { CardRepository.combineWeightedConceptGrades(listOf(Grade.GOOD to 0.0)) }
    }

    @Test
    fun `a card that contains none of a concept says nothing about it`() {
        val events = CardRepository.conceptReviewEvents(
            listOf(log(1, Grade.EASY, 10, session = 1), log(2, Grade.AGAIN, 11, session = 1))
        ) { cardId -> if (cardId == 2L) 0.0 else 1.0 }
        assertEquals(Grade.EASY, events.single().grade)
    }

    @Test
    fun `covering counts due concepts first, then how much of the card they are`() {
        val concepts = mapOf(1L to setOf(10L, 11L), 2L to setOf(10L))
        val shares = mapOf((1L to 10L) to 0.1, (1L to 11L) to 0.1, (2L to 10L) to 1.0)
        // Covering two due concepts beats covering one, however lightly.
        assertEquals(1L, CardRepository.pickBestCoveringCard(
            listOf(card(1), card(2)), concepts, uncovered = setOf(10L, 11L), randomize = false
        ) { c, k -> shares.getValue(c to k) }.id)
        // Each covering one: the card mostly about it wins.
        val one = mapOf(3L to setOf(10L, 20L), 4L to setOf(10L, 21L))
        val oneShares = mapOf((3L to 10L) to 0.25, (3L to 20L) to 0.75, (4L to 10L) to 0.5, (4L to 21L) to 0.5)
        assertEquals(4L, CardRepository.pickBestCoveringCard(
            listOf(card(3), card(4)), one, uncovered = setOf(10L), randomize = false
        ) { c, k -> oneShares.getValue(c to k) }.id)
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
