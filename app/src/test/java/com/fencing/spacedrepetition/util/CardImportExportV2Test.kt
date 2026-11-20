// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Dedicated test class for V2 format (Independent Learning) functionality.
 *
 * V2 format supports group-specific learning states, allowing the same card
 * to have different learning progress in different groups when independent
 * learning is enabled for those groups.
 *
 * Format structure:
 * - Header: #FSR_EXPORT_V2
 * - Column 4 (StateContext): "GLOBAL" for card's global state, or group name for group-specific state
 * - Multiple rows per card: One GLOBAL row + one row per group with independent learning
 */
class CardImportExportV2Test {

    // ==================== FORMAT DETECTION TESTS ====================

    // ==================== STATE CONTEXT PARSING TESTS ====================

    // ==================== COMPLEX SCENARIO TESTS ====================

    // ==================== EXPORT FORMAT TESTS ====================
    // Note: exportCardsWithGroupStates uses V3 format (the latest),
    // but V2 import parsing remains backward-compatible.

    @Test
    fun `Export with no independent learning groups`() {
        val card = Card(
            id = 1,
            question = "Test Question",
            answer = "Test Answer",
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val cardsWithStates = listOf(
            CardWithGroupStates(card, listOf("NormalGroup"), emptyMap())
        )

        val output = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(content.startsWith("# Fencing Spaced Repetition export\nversion: 5\n"))
        assertEquals(1, cardCount(content))
        assertEquals(0, groupStateCount(content))
    }

    @Test
    fun `Export with one independent learning group`() {
        val card = Card(
            id = 1,
            question = "Test Question",
            answer = "Test Answer",
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val groupState = CardLearningState(
            cardId = 1,
            learningGroupId = 2,
            fsrsStability = 10.0,
            fsrsDifficulty = 0.2,
            fsrsState = "REVIEW",
            fsrsReps = 6
        )

        val cardsWithStates = listOf(
            CardWithGroupStates(
                card,
                listOf("NormalGroup", "IndependentGroup"),
                mapOf("IndependentGroup" to groupState)
            )
        )

        val output = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount) // cards, not states

        val content = output.toString(Charsets.UTF_8.name())

        // One card entry, carrying its own state and the group's.
        assertEquals(1, cardCount(content))
        assertEquals(1, groupStateCount(content))
        assertTrue(content.contains("      stability: 5.0")) // Global stability
        assertTrue(content.contains("    learningStates:\n      - learningGroup: \"IndependentGroup\""))
        assertTrue(content.contains("        stability: 10.0")) // Group stability
    }

    @Test
    fun `Export with multiple independent learning groups`() {
        val card = Card(
            id = 1,
            question = "Test",
            answer = "Answer",
            fsrsStability = 1.0,
            fsrsReps = 1,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val group1State = CardLearningState(cardId = 1, learningGroupId = 1, fsrsStability = 2.0, fsrsReps = 2)
        val group2State = CardLearningState(cardId = 1, learningGroupId = 2, fsrsStability = 3.0, fsrsReps = 3)
        val group3State = CardLearningState(cardId = 1, learningGroupId = 3, fsrsStability = 4.0, fsrsReps = 4)

        val cardsWithStates = listOf(
            CardWithGroupStates(
                card,
                listOf("Normal", "IndGroup1", "IndGroup2", "IndGroup3"),
                mapOf(
                    "IndGroup1" to group1State,
                    "IndGroup2" to group2State,
                    "IndGroup3" to group3State
                )
            )
        )

        val output = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount) // cards, not states

        val content = output.toString(Charsets.UTF_8.name())
        assertEquals(1, cardCount(content))
        assertEquals(3, groupStateCount(content))
    }

    // ==================== ROUND-TRIP FORMAT TESTS ====================

    @Test
    fun `Round-trip export and import preserves all data`() {
        val originalCard = Card(
            id = 1,
            question = "Round trip V2 test",
            answer = "Answer with\nnewlines",
            fsrsStability = 5.5,
            fsrsDifficulty = 0.35,
            fsrsState = "REVIEW",
            fsrsReps = 4,
            fsrsLapses = 1,
            fsrsScheduledDays = 10,
            fsrsElapsedDays = 7,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val groupState = CardLearningState(
            cardId = 1,
            learningGroupId = 2,
            fsrsStability = 12.0,
            fsrsDifficulty = 0.2,
            fsrsState = "REVIEW",
            fsrsReps = 8,
            fsrsLapses = 0,
            fsrsScheduledDays = 20,
            fsrsElapsedDays = 14,
            nextReview = 1700200000000,
            lastReview = 1700000000000
        )

        val original = listOf(
            CardWithGroupStates(
                originalCard,
                listOf("NormalGroup", "IndependentGroup"),
                mapOf("IndependentGroup" to groupState)
            )
        )

        // Export
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(original, output, images = NoImages)

        // Import
        val (parsed, errors) = CardImportExport.parseCards(ByteArrayInputStream(output.toByteArray()))

        assertEquals(0, errors.size)
        assertEquals(2, parsed.size)

        // Verify global state
        val globalParsed = parsed.find { it.isGlobalState }!!
        assertEquals(originalCard.question, globalParsed.concept)
        assertEquals("Answer with\nnewlines", globalParsed.answer) // Newlines survive
        assertEquals(originalCard.fsrsStability, globalParsed.fsrsStability!!, 0.001)
        assertEquals(originalCard.fsrsDifficulty, globalParsed.fsrsDifficulty!!, 0.001)
        assertEquals(originalCard.fsrsState, globalParsed.fsrsState)
        assertEquals(originalCard.fsrsReps, globalParsed.fsrsReps)
        assertEquals(originalCard.fsrsLapses, globalParsed.fsrsLapses)

        // Verify group-specific state
        val groupParsed = parsed.find { it.isGroupSpecificState }!!
        assertEquals("IndependentGroup", groupParsed.stateContext)
        assertEquals(groupState.fsrsStability, groupParsed.fsrsStability!!, 0.001)
        assertEquals(groupState.fsrsDifficulty, groupParsed.fsrsDifficulty!!, 0.001)
        assertEquals(groupState.fsrsState, groupParsed.fsrsState)
        assertEquals(groupState.fsrsReps, groupParsed.fsrsReps)
        assertEquals(groupState.fsrsLapses, groupParsed.fsrsLapses)
    }

    // ==================== DETECTING INDEPENDENT LEARNING GROUPS ====================

    // ==================== EDGE CASES ====================

    @Test
    fun `Export multiple cards with varying independent learning states`() {
        val card1 = Card(id = 1, question = "Q1", answer = "A1",            fsrsStability = 1.0, created = 0, modified = 0)
        val card2 = Card(id = 2, question = "Q2", answer = "A2",            fsrsStability = 2.0, created = 0, modified = 0)
        val card3 = Card(id = 3, question = "Q3", answer = "A3",            fsrsStability = 3.0, created = 0, modified = 0)

        val groupState1 = CardLearningState(cardId = 1, learningGroupId = 1, fsrsStability = 5.0)
        val groupState2 = CardLearningState(cardId = 2, learningGroupId = 1, fsrsStability = 6.0)

        val cardsWithStates = listOf(
            CardWithGroupStates(card1, listOf("IndGroup"), mapOf("IndGroup" to groupState1)),
            CardWithGroupStates(card2, listOf("IndGroup"), mapOf("IndGroup" to groupState2)),
            CardWithGroupStates(card3, listOf("NormalGroup"), emptyMap())
        )

        val output = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output, images = NoImages)

        assertTrue(result is ExportResult.Success)
        // Three cards, whatever learning-group states they carry.
        assertEquals(3, (result as ExportResult.Success).exportedCount)

        val content = output.toString(Charsets.UTF_8.name())
        assertEquals(3, cardCount(content))
        assertEquals(2, groupStateCount(content))
    }

    /** How many card entries a YAML export wrote. */
    private fun cardCount(content: String): Int =
        Regex("^  - question:", RegexOption.MULTILINE).findAll(content).count()

    /** How many per-group learning states it wrote, across every card. */
    private fun groupStateCount(content: String): Int =
        Regex("^      - learningGroup:", RegexOption.MULTILINE).findAll(content).count()

    /**
     * Reads no images.
     *
     * These tests export cards that have none, and the real reader needs a
     * Context and confines itself to filesDir. Passed explicitly rather than
     * defaulted: a default that quietly reads nothing is how images would go
     * missing from a real export without anyone noticing.
     */
    private val NoImages = ImageReader { null }
}
