// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for opponent serialization (parseOpponentLine) and the opponent columns
 * added to review-history entries (opponentName, stabilityMultiplier).
 *
 * Features under test:
 * - Commit "Add opponent-skill grading to FSRS reviews" (3ad3384)
 * - Commit "Serialize opponents in TSV export/import" (4f7f321)
 */
class OpponentImportExportTest {

    // ==================== parseOpponentLine TESTS ====================

    // ==================== review history - opponent columns TESTS ====================

    // ==================== parsedReviewLogsToEntities - opponent resolution TESTS ====================

    @Test
    fun `parsedReviewLogsToEntities - resolves opponentId from name map`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card One", reviewTime = 1000L, grade = 3,
                algorithm = "FSRS", stateBefore = "NEW", stateAfter = "LEARNING",
                scheduledDays = 1, elapsedDays = 0, opponentName = "Alice"
            )
        )

        val result = CardImportExport.parsedReviewLogsToEntities(
            parsed,
            questionToCardId = mapOf("Card One" to 10L),
            opponentNameToId = mapOf("Alice" to 99L)
        )

        assertEquals(1, result.size)
        assertEquals(99L, result[0].opponentId)
    }

    @Test
    fun `parsedReviewLogsToEntities - null opponentId when name is not in map`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card One", reviewTime = 1000L, grade = 3,
                algorithm = "FSRS", stateBefore = "NEW", stateAfter = "LEARNING",
                scheduledDays = 1, elapsedDays = 0, opponentName = "Unknown Opponent"
            )
        )

        val result = CardImportExport.parsedReviewLogsToEntities(
            parsed,
            questionToCardId = mapOf("Card One" to 10L),
            opponentNameToId = mapOf("Alice" to 99L)
        )

        assertEquals(1, result.size)
        assertNull(result[0].opponentId)
    }

    @Test
    fun `parsedReviewLogsToEntities - null opponentId when opponentName is null`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card One", reviewTime = 1000L, grade = 3,
                algorithm = "FSRS", stateBefore = "NEW", stateAfter = "LEARNING",
                scheduledDays = 1, elapsedDays = 0, opponentName = null
            )
        )

        val result = CardImportExport.parsedReviewLogsToEntities(
            parsed,
            questionToCardId = mapOf("Card One" to 10L),
            opponentNameToId = mapOf("Alice" to 99L)
        )

        assertEquals(1, result.size)
        assertNull(result[0].opponentId)
    }

    @Test
    fun `parsedReviewLogsToEntities - preserves stabilityMultiplier on the entity`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card One", reviewTime = 1000L, grade = 3,
                algorithm = "FSRS", stateBefore = "NEW", stateAfter = "LEARNING",
                scheduledDays = 1, elapsedDays = 0, stabilityMultiplier = 1.75
            )
        )

        val result = CardImportExport.parsedReviewLogsToEntities(
            parsed,
            questionToCardId = mapOf("Card One" to 10L)
        )

        assertEquals(1, result.size)
        assertEquals(1.75, result[0].stabilityMultiplier, 0.0001)
    }

    @Test
    fun `parsedReviewLogsToEntities - default stabilityMultiplier is 1_0 when not set`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card One", reviewTime = 1000L, grade = 3,
                algorithm = "FSRS", stateBefore = "NEW", stateAfter = "LEARNING",
                scheduledDays = 1, elapsedDays = 0
            )
        )

        val result = CardImportExport.parsedReviewLogsToEntities(
            parsed,
            questionToCardId = mapOf("Card One" to 10L)
        )

        assertEquals(1, result.size)
        assertEquals(1.0, result[0].stabilityMultiplier, 0.0001)
    }

    @Test
    fun `parsedReviewLogsToEntities - handles multiple entries with different opponents`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card A", reviewTime = 1000L, grade = 3,
                algorithm = "FSRS", stateBefore = "NEW", stateAfter = "LEARNING",
                scheduledDays = 1, elapsedDays = 0, opponentName = "Alice", stabilityMultiplier = 1.5
            ),
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card B", reviewTime = 2000L, grade = 4,
                algorithm = "FSRS", stateBefore = "LEARNING", stateAfter = "REVIEW",
                scheduledDays = 3, elapsedDays = 1, opponentName = "Bob", stabilityMultiplier = 0.5
            ),
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Card C", reviewTime = 3000L, grade = 2,
                algorithm = "FSRS", stateBefore = "REVIEW", stateAfter = "RELEARNING",
                scheduledDays = 0, elapsedDays = 10, opponentName = null
            )
        )

        val result = CardImportExport.parsedReviewLogsToEntities(
            parsed,
            questionToCardId = mapOf("Card A" to 1L, "Card B" to 2L, "Card C" to 3L),
            opponentNameToId = mapOf("Alice" to 10L, "Bob" to 20L)
        )

        assertEquals(3, result.size)
        assertEquals(10L, result[0].opponentId)
        assertEquals(1.5, result[0].stabilityMultiplier, 0.0001)
        assertEquals(20L, result[1].opponentId)
        assertEquals(0.5, result[1].stabilityMultiplier, 0.0001)
        assertNull(result[2].opponentId)
        assertEquals(1.0, result[2].stabilityMultiplier, 0.0001)
    }
}
