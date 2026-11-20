// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningContextsTest {

    private val bouting = LearningGroup(id = 1, name = "Bouting", useConcepts = true)
    private val drills = LearningGroup(id = 2, name = "Drills") // concept scheduling off
    private val footwork = Group(id = 10, name = "Footwork", learningGroupId = 1, uid = "g-footwork")
    private val parries = Group(id = 11, name = "Parries", learningGroupId = 2, uid = "g-parries")
    private val warmUp = Group(id = 12, name = "Warm-up", uid = "g-warm-up")
    private val orphaned = Group(id = 13, name = "Orphaned", learningGroupId = 99, uid = "g-orphaned")
    // A second group called "Footwork", made later, learning Global.
    private val footworkAgain = Group(id = 14, name = "Footwork", uid = "g-footwork-2")
    private val contexts = LearningContexts(
        listOf(footworkAgain, footwork, parries, warmUp, orphaned),
        listOf(bouting, drills)
    )

    private fun log(groupUid: String?, groupName: String?) = ReviewLog(
        cardId = 1, sessionId = null, grade = 3, algorithm = "FSRS",
        stateBefore = "", stateAfter = "", scheduledDays = 0, elapsedDays = 0,
        groupUid = groupUid, groupName = groupName
    )

    @Test
    fun `a group learns in its learning group, or Global for none or one that is gone`() {
        assertEquals(1L, contexts.of(footwork))
        assertNull(contexts.of(warmUp))
        assertNull(contexts.of(orphaned))
        assertEquals(2L, contexts.ofGroupId(11))
        assertNull(contexts.ofGroupId(null))
        assertNull(contexts.ofGroupId(404))
    }

    @Test
    fun `a card is learnt in its groups' contexts, or Global with none`() {
        assertEquals(setOf<Long?>(1L, null), contexts.ofCardInGroups(setOf(10L, 12L)))
        assertEquals(setOf<Long?>(null), contexts.ofCardInGroups(emptySet()))
        assertEquals(setOf<Long?>(2L), contexts.ofCard(listOf(parries)))
    }

    @Test
    fun `a review is placed by its group's identifier, never by name when it has one`() {
        assertEquals(LearningContext(2L), contexts.ofReview(log("g-parries", "Renamed since")))
        // The group with that identifier is gone: not placed, even though a group has the name.
        assertNull(contexts.ofReview(log("g-deleted", "Parries")))
    }

    @Test
    fun `a review without an identifier is placed by name, the oldest group of it`() {
        assertEquals(footwork, contexts.groupOf(log(null, "Footwork")))
        assertEquals(LearningContext(1L), contexts.ofReview(log(null, "Footwork")))
        assertNull(contexts.ofReview(log(null, "Never existed")))
    }

    @Test
    fun `a review made in no group or the card editor is Global`() {
        assertEquals(LearningContext.GLOBAL, contexts.ofReview(log(null, null)))
        assertEquals(LearningContext.GLOBAL, contexts.ofReview(log(null, "")))
        assertEquals(LearningContext.GLOBAL, contexts.ofReview(log(null, LearningContexts.CARD_EDITOR)))
    }

    @Test
    fun `concept scheduling follows each context's switch`() {
        assertTrue(contexts.schedulesByConcept(1L, globalByConcept = false))
        assertFalse(contexts.schedulesByConcept(2L, globalByConcept = true))
        assertFalse(contexts.schedulesByConcept(99L, globalByConcept = true))
        assertEquals(setOf<Long?>(1L), contexts.schedulingByConcept(setOf(1L, 2L, null), false))
        assertEquals(setOf<Long?>(1L, null), contexts.schedulingByConcept(setOf(1L, 2L, null), true))
        assertEquals("Global", contexts.name(null))
        assertEquals("Drills", contexts.name(2L))
    }
}
