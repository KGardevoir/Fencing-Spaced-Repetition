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

    private fun offered(groupIds: Set<Long>, globalOn: Boolean = true) = offeredConceptIds(
        concepts, links, learningContextsOf(groupIds, groups), globalOn, learningGroups
    )

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
    fun `a context with concept scheduling off offers nothing`() {
        // Blade belongs to Drills, which does not schedule by concept.
        assertEquals(emptySet(), offered(setOf(11L)))
        // Global switched off: Tempo is not offered to a Global card.
        assertEquals(emptySet(), offered(setOf(12L), globalOn = false))
        assertEquals(setOf<Long?>(1L), conceptContexts(setOf(1L, 2L, null), false, learningGroups))
    }
}
