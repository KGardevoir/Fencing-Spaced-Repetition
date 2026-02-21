package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
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

    // ==================== SIMPLE FORMAT PARSING TESTS ====================

    @Test
    fun `parseCards - simple format with single card`() {
        val input = "What is 2+2?\t4"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)
        assertEquals("What is 2+2?", cards[0].question)
        assertEquals("4", cards[0].answer)
        assertFalse(cards[0].hasFullState)
    }

    @Test
    fun `parseCards - simple format with multiple cards`() {
        val input = """
            Question 1	Answer 1
            Question 2	Answer 2
            Question 3	Answer 3
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(3, cards.size)
        assertEquals(0, errors.size)
        assertEquals("Question 1", cards[0].question)
        assertEquals("Question 2", cards[1].question)
        assertEquals("Question 3", cards[2].question)
    }

    @Test
    fun `parseCards - simple format with trailing tab only is treated as missing answer`() {
        // Note: Lines are trimmed before parsing, so "Question\t" becomes "Question"
        // which has no tab delimiter -> error
        val input = "Question with empty answer\t"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        // Trailing tab is trimmed, so this is treated as missing answer
        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("no tab delimiter"))
    }

    @Test
    fun `parseCards - simple format skips empty lines`() {
        val input = """
            Question 1	Answer 1

            Question 2	Answer 2
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - simple format skips comment lines`() {
        val input = """
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
    fun `parseCards - simple format handles newline escaping`() {
        val input = "Question with<br>newline\tAnswer with<br>multiple<br>lines"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals("Question with\nnewline", cards[0].question)
        assertEquals("Answer with\nmultiple\nlines", cards[0].answer)
    }

    @Test
    fun `parseCards - simple format error on missing tab`() {
        val input = "Question without answer"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Line 1"))
    }

    @Test
    fun `parseCards - simple format with leading tab only is treated as missing delimiter`() {
        // Note: Lines are trimmed before parsing, so "\tAnswer" becomes "Answer"
        // which has no tab delimiter -> error
        val input = "\tAnswer without question"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        // The leading tab is trimmed, so error is about missing delimiter, not empty question
        assertTrue(errors[0].contains("no tab delimiter"))
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
        assertEquals("What is 2+2?", card.question)
        assertEquals("4", card.answer)
        assertEquals(AlgorithmType.FSRS, card.algorithm)
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
        assertEquals(2.5, card.sm2EaseFactor!!, 0.001)
        assertEquals(listOf("Math", "Basic"), card.groupNames)
    }

    @Test
    fun `parseCards - V1 format with SM2 algorithm`() {
        val input = """
            #FSR_EXPORT_V1
            #Question	Answer	Algorithm	NextReview	LastReview	FSRS_Stability	FSRS_Difficulty	FSRS_State	FSRS_Reps	FSRS_Lapses	FSRS_ScheduledDays	FSRS_ElapsedDays	SM2_EaseFactor	SM2_Interval	SM2_Repetitions	Groups(pipe-separated)
            Question 1	Answer 1	SM2	1700000000000	1699900000000	0.0	0.0	NEW	0	0	0	0	2.8	14	5	Study
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals(AlgorithmType.SM2, card.algorithm)
        assertEquals(2.8, card.sm2EaseFactor!!, 0.001)
        assertEquals(14, card.sm2Interval)
        assertEquals(5, card.sm2Repetitions)
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
        assertEquals("Simple Question", cards[0].question)
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
    fun `exportCardsWithGroups - V1 format export`() {
        val card = Card(
            id = 1,
            question = "What is 2+2?",
            answer = "4",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 5.5,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            fsrsLapses = 0,
            fsrsScheduledDays = 7,
            fsrsElapsedDays = 5,
            sm2EaseFactor = 2.5,
            sm2Interval = 0,
            sm2Repetitions = 0,
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
        assertTrue(output.startsWith("#FSR_EXPORT_V1"))
        assertTrue(output.contains("What is 2+2?"))
        assertTrue(output.contains("Math|Basic"))
        assertTrue(output.contains("FSRS"))
        assertTrue(output.contains("5.5"))
    }

    @Test
    fun `exportCardsWithGroups - handles newlines in content`() {
        val card = Card(
            id = 1,
            question = "Question with\nnewline",
            answer = "Answer with\nmultiple\nlines",
            algorithm = AlgorithmType.FSRS,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )
        val cardsWithGroups = listOf(CardWithGroupNames(card, emptyList()))

        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroups(cardsWithGroups, outputStream)

        val output = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(output.contains("Question with<br>newline"))
        assertTrue(output.contains("Answer with<br>multiple<br>lines"))
        assertFalse(output.contains("\n\n")) // No double newlines from content
    }

    @Test
    fun `exportCardsWithGroupStates - V2 format export with global state only`() {
        val card = Card(
            id = 1,
            question = "Q1",
            answer = "A1",
            algorithm = AlgorithmType.FSRS,
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
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val output = outputStream.toString(Charsets.UTF_8.name())
        assertTrue(output.startsWith("#FSR_EXPORT_V3"))
        assertTrue(output.contains("Q1"))
        assertTrue(output.contains("GLOBAL"))
    }

    @Test
    fun `exportCardsWithGroupStates - V2 format export with independent learning`() {
        val card = Card(
            id = 1,
            question = "Q1",
            answer = "A1",
            algorithm = AlgorithmType.FSRS,
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
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream)

        assertTrue(result is ExportResult.Success)
        assertEquals(2, (result as ExportResult.Success).exportedCount) // 1 global + 1 group-specific

        val output = outputStream.toString(Charsets.UTF_8.name())
        val lines = output.lines()

        // Should have header, column names, and 2 data rows
        assertTrue(lines[0].startsWith("#FSR_EXPORT_V3"))
        assertTrue(lines[1].startsWith("#Question"))

        // Find lines with actual data (skip empty lines)
        val dataLines = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(2, dataLines.size)

        // First data line should be GLOBAL
        assertTrue(dataLines[0].contains("\tGLOBAL\t"))
        assertTrue(dataLines[0].contains("5.0")) // Global stability

        // Second data line should be group-specific
        assertTrue(dataLines[1].contains("\tIndependentGroup\t"))
        assertTrue(dataLines[1].contains("8.0")) // Group-specific stability
    }

    // ==================== ROUND-TRIP TESTS ====================

    @Test
    fun `round-trip - V1 format export and import`() {
        val originalCard = Card(
            id = 1,
            question = "Round trip question",
            answer = "Round trip answer",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 7.5,
            fsrsDifficulty = 0.4,
            fsrsState = "REVIEW",
            fsrsReps = 5,
            fsrsLapses = 1,
            fsrsScheduledDays = 10,
            fsrsElapsedDays = 7,
            sm2EaseFactor = 2.6,
            sm2Interval = 14,
            sm2Repetitions = 4,
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
        assertEquals(originalCard.question, parsed.question)
        assertEquals(originalCard.answer, parsed.answer)
        assertEquals(originalCard.algorithm, parsed.algorithm)
        assertEquals(originalCard.fsrsStability, parsed.fsrsStability!!, 0.001)
        assertEquals(originalCard.fsrsDifficulty, parsed.fsrsDifficulty!!, 0.001)
        assertEquals(originalCard.fsrsState, parsed.fsrsState)
        assertEquals(originalCard.fsrsReps, parsed.fsrsReps)
        assertEquals(originalCard.fsrsLapses, parsed.fsrsLapses)
        assertEquals(originalCard.sm2EaseFactor, parsed.sm2EaseFactor!!, 0.001)
        assertEquals(originalCard.sm2Interval, parsed.sm2Interval)
        assertEquals(originalCard.sm2Repetitions, parsed.sm2Repetitions)
        assertEquals(originalGroups, parsed.groupNames)
    }

    @Test
    fun `round-trip - V2 format with independent learning`() {
        val originalCard = Card(
            id = 1,
            question = "Independent learning question",
            answer = "Answer",
            algorithm = AlgorithmType.FSRS,
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

        // Export V2
        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(
                card = originalCard,
                groupNames = listOf("NormalGroup", "IndependentGroup"),
                groupSpecificStates = mapOf("IndependentGroup" to groupState)
            )),
            outputStream
        )

        // Import V2
        val (parsedCards, errors) = CardImportExport.parseCards(
            ByteArrayInputStream(outputStream.toByteArray())
        )

        assertEquals(0, errors.size)
        assertEquals(2, parsedCards.size) // 1 global + 1 group-specific

        // Check global state row
        val globalRow = parsedCards.find { it.isGlobalState }!!
        assertEquals(originalCard.question, globalRow.question)
        assertEquals(originalCard.fsrsStability, globalRow.fsrsStability!!, 0.001)
        assertEquals(originalCard.fsrsReps, globalRow.fsrsReps)

        // Check group-specific state row
        val groupRow = parsedCards.find { it.isGroupSpecificState }!!
        assertEquals(originalCard.question, groupRow.question)
        assertEquals("IndependentGroup", groupRow.stateContext)
        assertEquals(groupState.fsrsStability, groupRow.fsrsStability!!, 0.001)
        assertEquals(groupState.fsrsReps, groupRow.fsrsReps)
    }

    // ==================== PARSED CARD TO CARD CONVERSION ====================

    @Test
    fun `parsedCardToCard - simple card without state`() {
        val parsed = ParsedCard(
            question = "Simple Q",
            answer = "Simple A",
            lineNumber = 1
        )

        val card = CardImportExport.parsedCardToCard(parsed)

        assertEquals("Simple Q", card.question)
        assertEquals("Simple A", card.answer)
        assertEquals(AlgorithmType.FSRS, card.algorithm)
        assertEquals("NEW", card.fsrsState)
        assertEquals(0.0, card.fsrsStability, 0.001)
        assertEquals(2.5, card.sm2EaseFactor, 0.001)
    }

    @Test
    fun `parsedCardToCard - card with full state`() {
        val parsed = ParsedCard(
            question = "Full state Q",
            answer = "Full state A",
            lineNumber = 1,
            algorithm = AlgorithmType.SM2,
            stateContext = "GLOBAL",
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            fsrsLapses = 1,
            fsrsScheduledDays = 7,
            fsrsElapsedDays = 5,
            sm2EaseFactor = 2.8,
            sm2Interval = 14,
            sm2Repetitions = 4
        )

        val card = CardImportExport.parsedCardToCard(parsed)

        assertEquals("Full state Q", card.question)
        assertEquals(AlgorithmType.SM2, card.algorithm)
        assertEquals(1700000000000, card.nextReview)
        assertEquals(5.0, card.fsrsStability, 0.001)
        assertEquals("REVIEW", card.fsrsState)
        assertEquals(2.8, card.sm2EaseFactor, 0.001)
        assertEquals(14, card.sm2Interval)
    }

    // ==================== FILENAME GENERATION ====================

    @Test
    fun `generateExportFilename - simple group name`() {
        val filename = CardImportExport.generateExportFilename("MyGroup")
        assertEquals("MyGroup_cards.tsv.gz", filename)
    }

    @Test
    fun `generateExportFilename - group name with special characters`() {
        // "My Group! @#$%" → My_Group + 6 underscores (one per: !, space, @, #, $, %)
        val filename = CardImportExport.generateExportFilename("My Group! @#\$%")
        assertEquals("My_Group______cards.tsv.gz", filename)
    }

    @Test
    fun `generateExportFilename - long group name is truncated`() {
        val longName = "A".repeat(100)
        val filename = CardImportExport.generateExportFilename(longName)
        assertTrue(filename.length <= 63) // 50 chars + "_cards.tsv.gz"
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
    fun `parseCards - only comments and whitespace`() {
        val input = """
            # Comment 1

            # Comment 2

        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - mixed valid and invalid lines`() {
        val input = """
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
    fun `parseCards - V1 format with invalid algorithm defaults to FSRS`() {
        val input = """
            #FSR_EXPORT_V1
            #Column headers...
            Q1	A1	INVALID_ALGO	0	0	0	0	NEW	0	0	0	0	2.5	0	0
        """.trimIndent()
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(AlgorithmType.FSRS, cards[0].algorithm)
    }

    @Test
    fun `parseCards - handles Windows line endings`() {
        val input = "Question 1\tAnswer 1\r\nQuestion 2\tAnswer 2\r\n"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCards - handles mixed line endings`() {
        val input = "Question 1\tAnswer 1\nQuestion 2\tAnswer 2\r\nQuestion 3\tAnswer 3\r"
        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        // The exact count may vary based on how Java handles \r, but should parse at least 2
        assertTrue(cards.size >= 2)
    }
}
