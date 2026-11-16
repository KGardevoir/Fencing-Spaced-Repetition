// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import com.fencing.spacedrepetition.data.model.LearningGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Learning concepts survive an archive export and import. */
class ConceptArchiveTest {

    private val noImages = ImageReader { null }

    private fun export(
        cards: List<CardWithGroupStates>,
        concepts: List<ConceptWithStates>,
        learningGroups: List<LearningGroup> = emptyList()
    ): String {
        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            cards, out, noImages, learningGroups = learningGroups, concepts = concepts
        )
        return out.toString()
    }

    @Test
    fun conceptsAndTheirStatesRoundTrip() {
        val distance = Concept(
            id = 1, name = "Distance", description = "Staying out of reach",
            fsrsStability = 4.5, fsrsDifficulty = 5.0, fsrsReps = 3, fsrsState = "REVIEW",
            lastReview = 1000L, nextReview = 9000L
        )
        val timing = Concept(id = 2, name = "Timing")
        val bouting = LearningGroup(id = 5, name = "Bouting")
        val text = export(
            cards = listOf(
                CardWithGroupStates(Card(id = 1, question = "Parry four", answer = ""), emptyList(),
                    conceptNames = listOf("Distance", "Timing")),
                CardWithGroupStates(Card(id = 2, question = "Lunge", answer = ""), emptyList())
            ),
            concepts = listOf(
                ConceptWithStates(
                    distance,
                    mapOf("Bouting" to ConceptLearningState(1, 5, fsrsStability = 2.0, fsrsReps = 1,
                        fsrsState = "LEARNING", nextReview = 7000L))
                ),
                ConceptWithStates(timing),
                // Carried by no exported card: left out.
                ConceptWithStates(Concept(id = 3, name = "Unused"))
            ),
            learningGroups = listOf(bouting)
        )

        assertTrue("concepts:" in text)
        assertTrue("Unused" !in text)
        // The learning group a concept state names is written so it can be found again.
        assertTrue("learningGroups:" in text)

        val (parsed, errors) = CardImportExport.parseCards(text.lines())
        assertEquals(emptyList(), errors)
        assertEquals(listOf("Distance", "Timing"), parsed.first { it.concept == "Parry four" }.conceptNames)
        assertEquals(emptyList(), parsed.first { it.concept == "Lunge" }.conceptNames)

        val concepts = CardImportExport.lastParsedConcepts.associateBy { it.name }
        assertEquals(setOf("Distance", "Timing"), concepts.keys)
        val d = concepts.getValue("Distance")
        assertEquals("Staying out of reach", d.description)
        assertEquals(4.5, d.globalState?.fsrsStability)
        assertEquals(9000L, d.globalState?.nextReview)
        assertEquals("REVIEW", d.globalState?.fsrsState)
        assertEquals(7000L, d.learningStates.getValue("Bouting").nextReview)
        assertEquals("LEARNING", d.learningStates.getValue("Bouting").fsrsState)
        // A new concept writes no state at all.
        assertNull(concepts.getValue("Timing").globalState)
        assertTrue(concepts.getValue("Timing").learningStates.isEmpty())
    }

    @Test
    fun aCardWithOnlyConceptsStillCarriesThem() {
        val text = export(
            cards = listOf(
                CardWithGroupStates(Card(id = 1, question = "Beat attack", answer = ""), emptyList(),
                    conceptNames = listOf("Tempo"))
            ),
            concepts = listOf(ConceptWithStates(Concept(id = 1, name = "Tempo")))
        )
        val (parsed, _) = CardImportExport.parseCards(text.lines())
        assertEquals(listOf("Tempo"), parsed.single().conceptNames)
    }

    @Test
    fun filesFromBeforeConceptsParseWithNone() {
        val (parsed, errors) = CardImportExport.parseCards(
            listOf("version: 5", "cards:", "  - question: \"Parry four\"", "    concept: \"ignored legacy alias\"")
        )
        assertEquals(emptyList(), errors)
        assertEquals(emptyList(), parsed.single().conceptNames)
        assertTrue(CardImportExport.lastParsedConcepts.isEmpty())
    }

    @Test
    fun aLearningGroupsConceptSwitchRoundTrips() {
        val on = LearningGroup(id = 5, name = "Bouting", useConcepts = true)
        val off = LearningGroup(id = 6, name = "Drills")
        val text = export(
            cards = listOf(
                CardWithGroupStates(Card(id = 1, question = "Lunge", answer = ""), emptyList(),
                    conceptNames = listOf("Distance"))
            ),
            concepts = listOf(
                ConceptWithStates(
                    Concept(id = 1, name = "Distance"),
                    mapOf(
                        "Bouting" to ConceptLearningState(1, 5, fsrsReps = 1, fsrsState = "LEARNING"),
                        "Drills" to ConceptLearningState(1, 6, fsrsReps = 2, fsrsState = "LEARNING")
                    )
                )
            ),
            learningGroups = listOf(on, off)
        )
        CardImportExport.parseCards(text.lines())
        assertEquals(setOf("Bouting", "Drills"), CardImportExport.lastParsedLearningGroups.keys)
        assertEquals(setOf("Bouting"), CardImportExport.lastParsedConceptLearningGroups)
    }

    @Test
    fun identifiersAndResetTimesRoundTrip() {
        val card = Card(id = 1, question = "Lunge", answer = "", lastReset = 1234L, fsrsReps = 2, fsrsState = "REVIEW")
        val lg = LearningGroup(id = 5, name = "Bouting")
        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            listOf(
                CardWithGroupStates(
                    card, listOf("Footwork"),
                    mapOf("Bouting" to com.fencing.spacedrepetition.data.model.CardLearningState(1, 5, fsrsReps = 1, lastReset = 99L)),
                    conceptNames = listOf("Distance")
                )
            ),
            out, noImages,
            groupSettings = listOf(com.fencing.spacedrepetition.data.model.Group(id = 3, name = "Footwork", learningGroupId = 5)),
            learningGroups = listOf(lg),
            concepts = listOf(ConceptWithStates(Concept(id = 1, name = "Distance")))
        )
        val text = out.toString()
        val (parsed, errors) = CardImportExport.parseCards(text.lines())
        assertEquals(emptyList(), errors)
        val own = parsed.first { it.isGlobalState }
        assertEquals(card.uid, own.uid)
        assertEquals(1234L, own.lastReset)
        assertEquals(99L, parsed.first { it.learningGroupState }.lastReset)
        assertEquals(mapOf("Footwork" to (CardImportExport.lastParsedGroupUids["Footwork"] ?: "")), CardImportExport.lastParsedGroupUids)
        assertTrue(CardImportExport.lastParsedGroupUids.getValue("Footwork").isNotBlank())
        assertEquals(lg.uid, CardImportExport.lastParsedLearningGroupUids["Bouting"])
        assertTrue(CardImportExport.lastParsedConcepts.single().uid!!.isNotBlank())
    }

    @Test
    fun aGroupWithoutSettingsIsStillListedSoItsIdentifierTravels() {
        val group = com.fencing.spacedrepetition.data.model.Group(id = 1, name = "Plain")
        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(Card(id = 1, question = "Q", answer = ""), listOf("Plain"))),
            out, noImages, groupSettings = listOf(group)
        )
        CardImportExport.parseCards(out.toString().lines())
        assertEquals(mapOf("Plain" to group.uid), CardImportExport.lastParsedGroupUids)
    }

    @Test
    fun aReviewCarriesTheIdentifiersOfEverythingItRefersTo() {
        val card = Card(id = 1, question = "Lunge", answer = "")
        val group = com.fencing.spacedrepetition.data.model.Group(id = 2, name = "Footwork")
        val alex = com.fencing.spacedrepetition.data.model.Opponent(id = 3, name = "Alex")
        val session = com.fencing.spacedrepetition.data.model.PracticeSession(id = 4, startTime = 500L)
        val log = com.fencing.spacedrepetition.data.model.ReviewLog(
            cardId = 1, sessionId = 4, reviewTime = 600L, grade = 3, algorithm = "FSRS", stateBefore = "",
            stateAfter = "", scheduledDays = 1, elapsedDays = 0, groupName = "Footwork", groupUid = group.uid,
            opponentId = 3, opponentUid = alex.uid
        )
        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("Footwork"))), out, noImages,
            groupSettings = listOf(group), reviewLogs = listOf(log), cardQuestions = mapOf(1L to "Lunge"),
            opponents = listOf(alex), opponentNamesById = mapOf(3L to "Alex"), sessions = listOf(session)
        )
        CardImportExport.parseCards(out.toString().lines())
        val parsed = CardImportExport.lastParsedReviewHistory.single()
        assertEquals(log.uid, parsed.uid)
        assertEquals(card.uid, parsed.cardUid)
        assertEquals(group.uid, parsed.groupUid)
        assertEquals(session.uid, parsed.sessionUid)
        assertEquals(alex.uid, parsed.opponentUid)
        assertEquals(session.uid, CardImportExport.lastParsedSessions.single().uid)
        assertEquals(alex.uid, CardImportExport.lastParsedOpponents.single().uid)
    }
}
