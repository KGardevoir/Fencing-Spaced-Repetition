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
    fun `covering scores by how much of the uncovered concepts a card contains`() {
        val concepts = mapOf(1L to setOf(10L, 11L), 2L to setOf(10L))
        val weights = mapOf((1L to 10L) to 0.2, (1L to 11L) to 0.2, (2L to 10L) to 1.0)
        val best = CardRepository.pickBestCoveringCard(
            listOf(card(1), card(2)), concepts, uncovered = setOf(10L, 11L), randomize = false
        ) { c, k -> weights.getValue(c to k) }
        assertEquals(2L, best.id)
    }
}
