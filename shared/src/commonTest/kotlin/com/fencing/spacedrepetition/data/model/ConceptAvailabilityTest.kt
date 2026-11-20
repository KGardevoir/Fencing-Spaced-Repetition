// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private val contexts = LearningContexts(groups, learningGroups)

    private fun offered(groupIds: Set<Long>) =
        offeredConceptIds(concepts, links, contexts.ofCardInGroups(groupIds))

    @Test
    fun `only concepts available in the card's contexts are offered`() {
        assertEquals(setOf(100L), offered(setOf(10L)))
        assertEquals(setOf(101L), offered(setOf(12L)))
        assertEquals(setOf(100L, 101L), offered(setOf(10L, 12L)))
    }

    @Test
    fun `the editor notes where concept scheduling is off`() {
        val offer = ConceptOffer(contexts, concepts, links)
        assertEquals(null, offer.schedulingOffNote(setOf(10L), globalByConcept = false))
        assertEquals(
            "Concept scheduling is off in Global: there, this card is scheduled card by card.",
            offer.schedulingOffNote(setOf(10L, 12L), globalByConcept = false)
        )
        assertTrue(offer.schedulingOffNote(setOf(11L), globalByConcept = true)!!.startsWith("Concept scheduling is off for Drills."))
        assertEquals(setOf(100L), offer.offeredFor(setOf(10L)))
    }

    @Test
    fun `tagging is allowed where concept scheduling is off`() {
        // Blade belongs to Drills, which does not schedule by concept: still offered.
        assertEquals(setOf(102L), offered(setOf(11L)))
    }

    @Test
    fun `a concept with no availability set is available everywhere`() {
        val anywhere = Concept(id = 103, name = "Footwork")
        val withIt = concepts + anywhere
        for (groupIds in listOf(setOf(10L), setOf(11L), setOf(12L), emptySet())) {
            val offered = offeredConceptIds(withIt, links, contexts.ofCardInGroups(groupIds))
            assertTrue(103L in offered, "not offered for $groupIds")
        }
        // Restricted concepts stay restricted.
        assertEquals(setOf(100L, 103L), offeredConceptIds(withIt, links, contexts.ofCardInGroups(setOf(10L))))
    }
}
