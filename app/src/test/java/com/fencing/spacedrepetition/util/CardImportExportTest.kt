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
 * Comprehensive unit tests for CardImportExport functionality.
 * Tests cover:
 * - Simple format parsing (question\tanswer)
 * - V1 format parsing (global learning state)
 * - V2 format parsing (group-specific learning states for independent learning)
 * - Export functionality for all formats
 * - Error handling and edge cases
 */
class CardImportExportTest {

    // ==================== HEADER VALIDATION TESTS ====================

    @Test
    fun `parseCards - rejects a file that is neither format`() {
        val input = "Question 1\tAnswer 1\nQuestion 2\tAnswer 2"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("cards:"))
    }

    // ==================== SIMPLE FORMAT PARSING TESTS ====================
    // Simple two-column (question\tanswer) data is accepted when inside a valid FSR export file.

    // ==================== V1 FORMAT PARSING TESTS ====================

    // ==================== V2 FORMAT PARSING TESTS (Independent Learning) ====================

    // ==================== EXPORT TESTS ====================

    @Test
    fun `exportCardsWithGroups - writes a YAML document`() {
        val card = Card(
            id = 1,
            question = "What is 2+2?",
            answer = "4",
            fsrsStability = 5.5,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            fsrsLapses = 0,
            fsrsScheduledDays = 7,
            fsrsElapsedDays = 5,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )
        val cardsWithGroups = listOf(CardWithGroupNames(card, listOf("Math", "Basic")))

        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroups(cardsWithGroups, outputStream)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val output = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(output.startsWith("# Fencing Spaced Repetition export\nversion: 5\n"))
        // Every string is quoted: "4", "no" and "12:30" are all ordinary
        // answers, and a YAML 1.1 reader takes an unquoted one for a number,
        // a boolean and a time respectively.
        assertTrue(output.contains("cards:\n  - question: \"What is 2+2?\"\n"))
        assertTrue(output.contains("answer: \"4\""))
        assertTrue(output.contains("groups:\n      - \"Math\"\n      - \"Basic\"\n"))
        assertTrue(output.contains("stability: 5.5"))
        assertTrue(output.contains("fsrsState: \"REVIEW\""))
        // Nothing tab-separated is written any more.
        assertFalse(output.contains("#FSR_EXPORT"))
        assertFalse(output.contains("\t"))
    }

    @Test
    fun `exportCardsWithGroups - newlines survive as a literal block`() {
        val card = Card(
            id = 1,
            question = "Question with\nnewline",
            answer = "Answer with\nmultiple\nlines",
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )
        val cardsWithGroups = listOf(CardWithGroupNames(card, emptyList()))

        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroups(cardsWithGroups, outputStream)

        val output = outputStream.toString(Charsets.UTF_8.name())
        // A newline is a newline now: the <br> placeholder went out with the
        // tab-separated format that had nowhere else to put one.
        assertFalse(output.contains("<br>"))
        assertTrue(output.contains("question: |-\n      Question with\n      newline\n"))
        assertTrue(
            output.contains("answer: |-\n      Answer with\n      multiple\n      lines\n")
        )

        // And comes back the way it went in.
        val (parsed, errors) = CardImportExport.parseCards(output.byteInputStream())
        assertEquals(0, errors.size)
        assertEquals("Question with\nnewline", parsed[0].concept)
        assertEquals("Answer with\nmultiple\nlines", parsed[0].answer)
    }

    @Test
    fun `exportCardsWithGroupStates - global state only`() {
        val card = Card(
            id = 1,
            question = "Q1",
            answer = "A1",
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )
        val cardsWithStates = listOf(
            CardWithGroupStates(
                card = card,
                groupNames = listOf("Math"),
                learningStates = emptyMap()
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val output = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(output.startsWith("# Fencing Spaced Repetition export\nversion: 5\n"))
        assertTrue(output.contains("- question: \"Q1\""))
        assertTrue(output.contains("state:"))
        // A card with no independent group keeps no per-group states.
        assertFalse(output.contains("learningStates:"))
    }

    @Test
    fun `exportCardsWithGroupStates - independent learning`() {
        val card = Card(
            id = 1,
            question = "Q1",
            answer = "A1",
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val groupLearningState = CardLearningState(
            cardId = 1,
            learningGroupId = 2,
            fsrsStability = 8.0,
            fsrsDifficulty = 0.25,
            fsrsState = "REVIEW",
            fsrsReps = 5,
            fsrsLapses = 1,
            nextReview = 1700100000000,
            lastReview = 1699950000000
        )

        val cardsWithStates = listOf(
            CardWithGroupStates(
                card = card,
                groupNames = listOf("Math", "IndependentGroup"),
                learningStates = mapOf("IndependentGroup" to groupLearningState)
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount) // cards, not states

        val output = outputStream.toString(Charsets.UTF_8.name())

        // One card, carrying its own state and the group's.
        assertEquals(1, Regex("^  - question:", RegexOption.MULTILINE).findAll(output).count())
        assertTrue(output.contains("groups:\n      - \"Math\"\n      - \"IndependentGroup\"\n"))
        assertTrue(output.contains("    state:\n      nextReview: 1700000000000"))
        assertTrue(output.contains("      stability: 5.0"))
        assertTrue(output.contains("    learningStates:\n      - learningGroup: \"IndependentGroup\""))
        assertTrue(output.contains("        stability: 8.0"))

        // And reads back as the two rows the importer expects.
        val (parsed, errors) = CardImportExport.parseCards(output.byteInputStream())
        assertEquals(0, errors.size)
        assertEquals(2, parsed.size)
        assertEquals(5.0, parsed.first { it.isGlobalState }.fsrsStability!!, 0.001)
        assertEquals(8.0, parsed.first { it.isGroupSpecificState }.fsrsStability!!, 0.001)
    }

    // ==================== ROUND-TRIP TESTS ====================

    @Test
    fun `round-trip - cards with their groups, exported and imported`() {
        val originalCard = Card(
            id = 1,
            question = "Round trip question",
            answer = "Round trip answer",
            fsrsStability = 7.5,
            fsrsDifficulty = 0.4,
            fsrsState = "REVIEW",
            fsrsReps = 5,
            fsrsLapses = 1,
            fsrsScheduledDays = 10,
            fsrsElapsedDays = 7,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )
        val originalGroups = listOf("Group1", "Group2")

        // Export
        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroups(
            listOf(CardWithGroupNames(originalCard, originalGroups)),
            outputStream
        )

        // Import
        val (parsedCards, errors) = CardImportExport.parseCards(
            ByteArrayInputStream(outputStream.toByteArray())
        )

        assertEquals(0, errors.size)
        assertEquals(1, parsedCards.size)

        val parsed = parsedCards[0]
        assertEquals(originalCard.question, parsed.concept)
        assertEquals(originalCard.answer, parsed.answer)
        assertEquals(originalCard.fsrsStability, parsed.fsrsStability!!, 0.001)
        assertEquals(originalCard.fsrsDifficulty, parsed.fsrsDifficulty!!, 0.001)
        assertEquals(originalCard.fsrsState, parsed.fsrsState)
        assertEquals(originalCard.fsrsReps, parsed.fsrsReps)
        assertEquals(originalCard.fsrsLapses, parsed.fsrsLapses)
        assertEquals(originalCard.fsrsScheduledDays, parsed.fsrsScheduledDays)
        assertEquals(originalCard.fsrsElapsedDays, parsed.fsrsElapsedDays)
        assertEquals(originalGroups, parsed.groupNames)
    }

    @Test
    fun `round-trip - independent learning states`() {
        val originalCard = Card(
            id = 1,
            question = "Independent learning question",
            answer = "Answer",
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val groupState = CardLearningState(
            cardId = 1,
            learningGroupId = 2,
            fsrsStability = 10.0,
            fsrsDifficulty = 0.2,
            fsrsState = "REVIEW",
            fsrsReps = 6,
            fsrsLapses = 0,
            nextReview = 1700200000000,
            lastReview = 1700000000000
        )

        // Export
        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(
                card = originalCard,
                groupNames = listOf("NormalGroup", "IndependentGroup"),
                learningStates = mapOf("IndependentGroup" to groupState)
            )),
            outputStream,
            images = NoImages
        )

        // Import
        val (parsedCards, errors) = CardImportExport.parseCards(
            ByteArrayInputStream(outputStream.toByteArray())
        )

        assertEquals(0, errors.size)
        assertEquals(2, parsedCards.size) // 1 global + 1 group-specific

        // Check global state row
        val globalRow = parsedCards.find { it.isGlobalState }!!
        assertEquals(originalCard.question, globalRow.concept)
        assertEquals(originalCard.fsrsStability, globalRow.fsrsStability!!, 0.001)
        assertEquals(originalCard.fsrsReps, globalRow.fsrsReps)

        // Check group-specific state row
        val groupRow = parsedCards.find { it.isGroupSpecificState }!!
        assertEquals(originalCard.question, groupRow.concept)
        assertEquals("IndependentGroup", groupRow.stateContext)
        assertEquals(groupState.fsrsStability, groupRow.fsrsStability!!, 0.001)
        assertEquals(groupState.fsrsReps, groupRow.fsrsReps)
    }

    // ==================== PARSED CARD TO CARD CONVERSION ====================

    @Test
    fun `parsedCardToCard - simple card without state`() {
        val parsed = ParsedCard(
            concept = "Simple Q",
            answer = "Simple A",
            lineNumber = 1
        )

        val card = CardImportExport.parsedCardToCard(parsed)

        assertEquals("Simple Q", card.question)
        assertEquals("Simple A", card.answer)
        assertEquals("NEW", card.fsrsState)
        assertEquals(0.0, card.fsrsStability, 0.001)
    }

    @Test
    fun `parsedCardToCard - card with full state`() {
        val parsed = ParsedCard(
            concept = "Full state Q",
            answer = "Full state A",
            lineNumber = 1,
            stateContext = "GLOBAL",
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            fsrsLapses = 1,
            fsrsScheduledDays = 7,
            fsrsElapsedDays = 5
        )

        val card = CardImportExport.parsedCardToCard(parsed)

        assertEquals("Full state Q", card.question)
        assertEquals(1700000000000, card.nextReview)
        assertEquals(5.0, card.fsrsStability, 0.001)
        assertEquals("REVIEW", card.fsrsState)
        assertEquals(3, card.fsrsReps)
        assertEquals(7, card.fsrsScheduledDays)
    }

    // ==================== FILENAME GENERATION ====================

    // Every export is named "<when>_<what>", so these check the two halves
    // separately: the stamp against a fixed instant, and the rest against the
    // names the files have always had.
    private val stamp = Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_")

    private fun withoutStamp(filename: String): String {
        assertTrue("no timestamp in $filename", stamp.containsMatchIn(filename))
        return filename.replaceFirst(stamp, "")
    }

    @Test
    fun `exportFilename - the time comes first, then what the file holds`() {
        assertEquals(
            "2023-11-14_22-13-20_all_cards.yaml.gz",
            CardImportExport.exportFilename(
                "all_cards.yaml.gz",
                at = 1_700_000_000_000L,
                utcOffsetSeconds = 0
            )
        )
    }

    @Test
    fun `generateExportFilename - simple group name`() {
        val filename = CardImportExport.generateExportFilename("MyGroup")
        assertEquals("MyGroup_cards.yaml.gz", withoutStamp(filename))
    }

    @Test
    fun `generateExportFilename - group name with special characters`() {
        // "My Group! @#$%" sanitizes to "My_Group______" (space between My/Group + !, space, @, #, $, %)
        // then the function appends "_cards.yaml.gz", giving 7 underscores before "cards"
        val filename = CardImportExport.generateExportFilename("My Group! @#\$%")
        assertEquals("My_Group_______cards.yaml.gz", withoutStamp(filename))
    }

    @Test
    fun `generateExportFilename - long group name is truncated`() {
        val longName = "A".repeat(100)
        val filename = CardImportExport.generateExportFilename(longName)
        assertTrue(withoutStamp(filename).length <= 64) // 50 chars + "_cards.yaml.gz"
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `parseCards - empty input`() {
        val input = ""
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(0, errors.size)
    }

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
