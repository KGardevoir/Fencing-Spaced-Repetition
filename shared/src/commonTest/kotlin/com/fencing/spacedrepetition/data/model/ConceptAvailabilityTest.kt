// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ConceptAvailabilityTest {

    private val bouting = LearningGroup(id = 1, name = "Bouting", useConcepts = true)
    private val drills = LearningGroup(id = 2, name = "Drills") // concept scheduling off
    private val learningGroups = listOf(bouting, drills)
    private val groups = listOf(
        Group(id = 10, name = "Footwork", learningGroupId = 1),
        Group(id = 11, name = "Parries", learningGroupId = 2),
        Group(id = 12, name = "Warm-up")
    )
    private val distance = Concept(id = 100, name = "Distance")
    private val tempo = Concept(id = 101, name = "Tempo", inGlobal = true)
    private val blade = Concept(id = 102, name = "Blade")
    private val concepts = listOf(distance, tempo, blade)
    private val links = listOf(
        ConceptLearningGroupCrossRef(100, 1),
        ConceptLearningGroupCrossRef(102, 2)
    )

    private fun offered(groupIds: Set<Long>) =
        offeredConceptIds(concepts, links, learningContextsOf(groupIds, groups))

    @Test
    fun `a card is learnt in its groups' learning contexts, or Global with none`() {
        assertEquals(setOf<Long?>(1L, null), learningContextsOf(setOf(10L, 12L), groups))
        assertEquals(setOf<Long?>(null), learningContextsOf(emptySet(), groups))
    }

    @Test
    fun `only concepts available in the card's contexts are offered`() {
        assertEquals(setOf(100L), offered(setOf(10L)))
        assertEquals(setOf(101L), offered(setOf(12L)))
        assertEquals(setOf(100L, 101L), offered(setOf(10L, 12L)))
    }

    @Test
    fun `tagging is allowed where concept scheduling is off`() {
        // Blade belongs to Drills, which does not schedule by concept: still offered.
        assertEquals(setOf(102L), offered(setOf(11L)))
    }

    @Test
    fun `concept contexts are the ones whose switch is on`() {
        assertEquals(setOf<Long?>(1L), conceptContexts(setOf(1L, 2L, null), false, learningGroups))
        assertEquals(setOf<Long?>(1L, null), conceptContexts(setOf(1L, 2L, null), true, learningGroups))
        assertEquals("Global", contextName(null, learningGroups))
        assertEquals("Drills", contextName(2L, learningGroups))
    }
}
