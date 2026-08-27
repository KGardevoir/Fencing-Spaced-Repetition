// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
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
        assertTrue(errors[0].contains("FSR_EXPORT"))
    }

    @Test
    fun `parseCards - rejects a comment followed by tab-separated lines`() {
        val input = "# This is not a valid header\nQuestion 1\tAnswer 1"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("FSR_EXPORT"))
    }

    // ==================== SIMPLE FORMAT PARSING TESTS ====================
    // Simple two-column (question\tanswer) data is accepted when inside a valid FSR export file.

    @Test
    fun `parseCards - two-column data with V1 header parses correctly`() {
        val input = "#FSR_EXPORT_V1\nWhat is 2+2?\t4"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)
        assertEquals("What is 2+2?", cards[0].concept)
        assertEquals("4", cards[0].answer)
        assertFalse(cards[0].hasFullState)
    }

    @Test
    fun `parseCards - multiple two-column rows with V1 header`() {
        val input = """
            #FSR_EXPORT_V1
            Question 1	Answer 1
            Question 2	Answer 2
            Question 3	Answer 3
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(3, cards.size)
        assertEquals(0, errors.size)
        assertEquals("Question 1", cards[0].concept)
        assertEquals("Question 2", cards[1].concept)
        assertEquals("Question 3", cards[2].concept)
    }

    @Test
    fun `parseCards - trailing tab trimmed produces missing answer error`() {
        // Lines are trimmed before parsing, so "Question\t" becomes "Question" -> missing answer
        val input = "#FSR_EXPORT_V1\nQuestion with empty answer\t"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Missing answer"))
    }

    @Test
    fun `parseCards - skips empty lines within V1 file`() {
        val input = """
            #FSR_EXPORT_V1
            Question 1	Answer 1

            Question 2	Answer 2
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - skips comment lines within V1 file`() {
        val input = """
            #FSR_EXPORT_V1
            # This is a comment
            Question 1	Answer 1
            #Another comment
            Question 2	Answer 2
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - handles newline escaping within V1 file`() {
        val input = "#FSR_EXPORT_V1\nQuestion with<br>newline\tAnswer with<br>multiple<br>lines"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals("Question with\nnewline", cards[0].concept)
        assertEquals("Answer with\nmultiple\nlines", cards[0].answer)
    }

    @Test
    fun `parseCards - error on missing tab within V1 file`() {
        val input = "#FSR_EXPORT_V1\nQuestion without answer"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Line 2"))
    }

    @Test
    fun `parseCards - leading tab trimmed produces missing answer error`() {
        // Lines are trimmed before parsing, so "\tAnswer" becomes "Answer" -> missing answer
        val input = "#FSR_EXPORT_V1\n\tAnswer without question"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Missing answer"))
    }

    // ==================== V1 FORMAT PARSING TESTS ====================

    @Test
    fun `parseCards - V1 format with full learning state`() {
        val input = """
            #FSR_EXPORT_V1
            #Question	Answer	Algorithm	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            What is 2+2?	4	FSRS	1700000000000	1699900000000	5.5	0.3	REVIEW	3	0	7	5	2.5	0	0	Math|Basic
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)

        val card = cards[0]
        assertTrue(card.hasFullState)
        assertTrue(card.isGlobalState)
        assertFalse(card.isGroupSpecificState)
        assertEquals("What is 2+2?", card.concept)
        assertEquals("4", card.answer)
        assertEquals("GLOBAL", card.stateContext)
        assertEquals(1700000000000L, card.nextReview)
        assertEquals(1699900000000L, card.lastReview)
        assertEquals(5.5, card.fsrsStability!!, 0.001)
        assertEquals(0.3, card.fsrsDifficulty!!, 0.001)
        assertEquals("REVIEW", card.fsrsState)
        assertEquals(3, card.fsrsReps)
        assertEquals(0, card.fsrsLapses)
        assertEquals(7, card.fsrsScheduledDays)
        assertEquals(5, card.fsrsElapsedDays)
        assertEquals(listOf("Math", "Basic"), card.groupNames)
    }

    @Test
    fun `parseCards - V1 row naming SM-2 still imports, SM-2 columns ignored`() {
        val input = """
            #FSR_EXPORT_V1
            #Question	Answer	Algorithm	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            Question 1	Answer 1	SM2	1700000000000	1699900000000	0.0	0.0	NEW	0	0	0	0	2.8	14	5	Study
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("Question 1", card.concept)
        assertEquals("Answer 1", card.answer)
        assertTrue(card.hasFullState)
        assertEquals("GLOBAL", card.stateContext)
        assertEquals(1700000000000L, card.nextReview)
        assertEquals(listOf("Study"), card.groupNames)
    }

    @Test
    fun `parseCards - V1 format with multiple groups`() {
        val input = """
            #FSR_EXPORT_V1
            #Question	Answer	Algorithm	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            Q1	A1	FSRS	0	0	0	0	NEW	0	0	0	0	2.5	0	0	Group1|Group2|Group3
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(listOf("Group1", "Group2", "Group3"), cards[0].groupNames)
    }

    @Test
    fun `parseCards - V1 format with no groups`() {
        val input = """
            #FSR_EXPORT_V1
            #Question	Answer	Algorithm	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            Q1	A1	FSRS	0	0	0	0	NEW	0	0	0	0	2.5	0	0
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertTrue(cards[0].groupNames.isEmpty())
    }

    @Test
    fun `parseCards - V1 format fallback to simple when only 2 columns`() {
        val input = """
            #FSR_EXPORT_V1
            #Question	Answer	Algorithm...
            Simple Question	Simple Answer
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertFalse(cards[0].hasFullState)
        assertEquals("Simple Question", cards[0].concept)
        assertEquals("Simple Answer", cards[0].answer)
    }

    // ==================== V2 FORMAT PARSING TESTS (Independent Learning) ====================

    @Test
    fun `parseCards - V2 format with global state only`() {
        val input = """
            #FSR_EXPORT_V2
            #Question	Answer	Algorithm	StateContext	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            What is 2+2?	4	FSRS	GLOBAL	1700000000000	1699900000000	5.5	0.3	REVIEW	3	0	7	5	2.5	0	0	Math|Basic
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)

        val card = cards[0]
        assertTrue(card.hasFullState)
        assertTrue(card.isGlobalState)
        assertFalse(card.isGroupSpecificState)
        assertEquals("GLOBAL", card.stateContext)
    }

    @Test
    fun `parseCards - V2 format with group-specific state`() {
        val input = """
            #FSR_EXPORT_V2
            #Question	Answer	Algorithm	StateContext	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            What is 2+2?	4	FSRS	MyStudyGroup	1700000000000	1699900000000	10.0	0.2	REVIEW	5	1	14	7	2.5	0	0	MyStudyGroup
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)

        val card = cards[0]
        assertTrue(card.hasFullState)
        assertFalse(card.isGlobalState)
        assertTrue(card.isGroupSpecificState)
        assertEquals("MyStudyGroup", card.stateContext)
        assertEquals(10.0, card.fsrsStability!!, 0.001)
        assertEquals(5, card.fsrsReps)
    }

    @Test
    fun `parseCards - V2 format with multiple rows per card (global + group-specific)`() {
        val input = """
            #FSR_EXPORT_V2
            #Question	Answer	Algorithm	StateContext	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            What is 2+2?	4	FSRS	GLOBAL	1700000000000	1699900000000	5.0	0.3	REVIEW	3	0	7	5	2.5	0	0	Math|IndependentGroup
            What is 2+2?	4	FSRS	IndependentGroup	1700100000000	1699950000000	8.0	0.25	REVIEW	4	0	10	6	2.5	0	0	IndependentGroup
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)

        // First row is global state
        val globalCard = cards[0]
        assertTrue(globalCard.isGlobalState)
        assertEquals("GLOBAL", globalCard.stateContext)
        assertEquals(5.0, globalCard.fsrsStability!!, 0.001)
        assertEquals(3, globalCard.fsrsReps)

        // Second row is group-specific state
        val groupCard = cards[1]
        assertTrue(groupCard.isGroupSpecificState)
        assertEquals("IndependentGroup", groupCard.stateContext)
        assertEquals(8.0, groupCard.fsrsStability!!, 0.001)
        assertEquals(4, groupCard.fsrsReps)
    }

    @Test
    fun `parseCards - V2 format detects independent learning groups`() {
        val input = """
            #FSR_EXPORT_V2
            #Question	Answer	Algorithm	StateContext	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            Q1	A1	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	GroupA|GroupB
            Q1	A1	FSRS	GroupA	0	0	1.0	0.1	LEARNING	1	0	1	0	2.5	0	0	GroupA
            Q2	A2	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	GroupB|GroupC
            Q2	A2	FSRS	GroupC	0	0	2.0	0.2	REVIEW	2	0	2	1	2.5	0	0	GroupC
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(4, cards.size)

        // Identify group-specific rows
        val groupSpecificCards = cards.filter { it.isGroupSpecificState }
        assertEquals(2, groupSpecificCards.size)

        val independentGroups = groupSpecificCards.mapNotNull { it.stateContext }.toSet()
        assertEquals(setOf("GroupA", "GroupC"), independentGroups)
    }

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
        // Quoted, because a plain scalar starting with anything but a letter
        // -- or holding a "?" -- is where YAML gets ambiguous.
        assertTrue(output.contains("cards:\n  - question: \"What is 2+2?\"\n"))
        assertTrue(output.contains("answer: \"4\""))
        assertTrue(output.contains("groups: [Math, Basic]"))
        assertTrue(output.contains("stability: 5.5"))
        assertTrue(output.contains("fsrsState: REVIEW"))
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
                groupSpecificStates = emptyMap()
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val output = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(output.startsWith("# Fencing Spaced Repetition export\nversion: 5\n"))
        assertTrue(output.contains("- question: Q1"))
        assertTrue(output.contains("state:"))
        // A card with no independent group keeps no per-group states.
        assertFalse(output.contains("groupStates:"))
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

        val groupLearningState = CardGroupLearningState(
            cardId = 1,
            groupId = 2,
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
                groupSpecificStates = mapOf("IndependentGroup" to groupLearningState)
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(2, (result as ExportResult.Success).exportedCount) // 1 global + 1 group-specific

        val output = outputStream.toString(Charsets.UTF_8.name())

        // One card, carrying its own state and the group's.
        assertEquals(1, Regex("^  - question:", RegexOption.MULTILINE).findAll(output).count())
        assertTrue(output.contains("groups: [Math, IndependentGroup]"))
        assertTrue(output.contains("    state:\n      nextReview: 1700000000000"))
        assertTrue(output.contains("      stability: 5.0"))
        assertTrue(output.contains("    groupStates:\n      - group: IndependentGroup"))
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

        val groupState = CardGroupLearningState(
            cardId = 1,
            groupId = 2,
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
                groupSpecificStates = mapOf("IndependentGroup" to groupState)
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

    @Test
    fun `parseCards - only comments and whitespace within valid V1 file`() {
        val input = """
            #FSR_EXPORT_V1
            # Comment 1

            # Comment 2

        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - mixed valid and invalid lines within V1 file`() {
        val input = """
            #FSR_EXPORT_V1
            Valid question 1	Valid answer 1
            Invalid line without tab
            Valid question 2	Valid answer 2
            	Empty question
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(2, errors.size)
    }

    @Test
    fun `parseCards - V1 format ignores whatever the Algorithm column says`() {
        val input = """
            #FSR_EXPORT_V1
            #Column headers...
            Q1	A1	INVALID_ALGO	0	0	0	0	NEW	0	0	0	0	2.5	0	0
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals("Q1", cards[0].concept)
        assertEquals("A1", cards[0].answer)
    }

    @Test
    fun `parseCards - handles Windows line endings`() {
        val input = "#FSR_EXPORT_V1\r\nQuestion 1\tAnswer 1\r\nQuestion 2\tAnswer 2\r\n"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - handles mixed line endings`() {
        val input = "#FSR_EXPORT_V1\nQuestion 1\tAnswer 1\nQuestion 2\tAnswer 2\r\nQuestion 3\tAnswer 3\r"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        // The exact count may vary based on how Java handles \r, but should parse at least 2
        assertTrue(cards.size >= 2)
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
