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

    @Test
    fun `parseOpponentLine - returns null for non-opponent lines`() {
        assertNull(CardImportExport.parseOpponentLine("#GROUP:Sabre"))
        assertNull(CardImportExport.parseOpponentLine("#FSR_EXPORT_V3"))
        assertNull(CardImportExport.parseOpponentLine("regular data line"))
        assertNull(CardImportExport.parseOpponentLine(""))
    }

    @Test
    fun `parseOpponentLine - parses name and skillMultiplier`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Alice\tskillMultiplier=1.5")

        assertNotNull(result)
        assertEquals("Alice", result!!.name)
        assertEquals(1.5, result.skillMultiplier, 0.0001)
        assertEquals("", result.notes)
    }

    @Test
    fun `parseOpponentLine - parses name skillMultiplier and notes`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Bob\tskillMultiplier=0.75\tnotes=Club champion")

        assertNotNull(result)
        assertEquals("Bob", result!!.name)
        assertEquals(0.75, result.skillMultiplier, 0.0001)
        assertEquals("Club champion", result.notes)
    }

    @Test
    fun `parseOpponentLine - defaults skillMultiplier to 1_0 when attribute is absent`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Charlie")

        assertNotNull(result)
        assertEquals("Charlie", result!!.name)
        assertEquals(1.0, result.skillMultiplier, 0.0001)
    }

    @Test
    fun `parseOpponentLine - returns null for empty name`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:\tskillMultiplier=1.0")
        assertNull(result)
    }

    @Test
    fun `parseOpponentLine - unescapes newlines in name`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Alice<br>Smith\tskillMultiplier=1.0")

        assertNotNull(result)
        assertEquals("Alice\nSmith", result!!.name)
    }

    @Test
    fun `parseOpponentLine - unescapes newlines in notes`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Dave\tskillMultiplier=1.2\tnotes=Line one<br>Line two")

        assertNotNull(result)
        assertEquals("Line one\nLine two", result!!.notes)
    }

    @Test
    fun `parseOpponentLine - ignores unknown attribute keys`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Eve\tskillMultiplier=1.3\tunknownKey=value")

        assertNotNull(result)
        assertEquals("Eve", result!!.name)
        assertEquals(1.3, result.skillMultiplier, 0.0001)
    }

    @Test
    fun `parseOpponentLine - handles half-point multiplier used as preset`() {
        val result = CardImportExport.parseOpponentLine("#OPPONENT:Beginner\tskillMultiplier=0.5")

        assertNotNull(result)
        assertEquals(0.5, result!!.skillMultiplier, 0.0001)
    }

    // ==================== review history - opponent columns TESTS ====================

    @Test
    fun `parseReviewHistory - parses opponentName and stabilityMultiplier from columns 11 and 12`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "What is a lunge?\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0\t\t\t\tAlice\t1.5",
            "#REVIEW_HISTORY_END"
        )

        val result = CardImportExport.parseReviewHistory(lines)

        assertEquals(1, result.size)
        assertEquals("Alice", result[0].opponentName)
        assertEquals(1.5, result[0].stabilityMultiplier, 0.0001)
    }

    @Test
    fun `parseReviewHistory - null opponentName when column 11 is blank`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "What is a lunge?\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0\t\t\t\t\t1.0",
            "#REVIEW_HISTORY_END"
        )

        val result = CardImportExport.parseReviewHistory(lines)

        assertEquals(1, result.size)
        assertNull(result[0].opponentName)
    }

    @Test
    fun `parseReviewHistory - stabilityMultiplier defaults to 1_0 when column 12 is absent`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "What is a lunge?\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0",
            "#REVIEW_HISTORY_END"
        )

        val result = CardImportExport.parseReviewHistory(lines)

        assertEquals(1, result.size)
        assertEquals(1.0, result[0].stabilityMultiplier, 0.0001)
    }

    @Test
    fun `parseReviewHistory - stabilityMultiplier defaults to 1_0 when column 12 is non-numeric`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "Card\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0\t\t\t\tAlice\tnot-a-number",
            "#REVIEW_HISTORY_END"
        )

        val result = CardImportExport.parseReviewHistory(lines)

        assertEquals(1, result.size)
        assertEquals(1.0, result[0].stabilityMultiplier, 0.0001)
    }

    @Test
    fun `parseReviewHistory - unescapes newlines in opponentName`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "Card\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0\t\t\t\tAlice<br>Smith\t1.0",
            "#REVIEW_HISTORY_END"
        )

        val result = CardImportExport.parseReviewHistory(lines)

        assertEquals(1, result.size)
        assertEquals("Alice\nSmith", result[0].opponentName)
    }

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
