package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.ReviewLog
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ReviewHistoryImportExportTest {

    private fun makeReviewLog(
        cardId: Long,
        grade: Int = 3,
        reviewTime: Long = 1000L,
        algorithm: String = "FSRS",
        stateBefore: String = "NEW",
        stateAfter: String = "LEARNING",
        scheduledDays: Int = 1,
        elapsedDays: Int = 0
    ) = ReviewLog(
        id = 0,
        cardId = cardId,
        sessionId = null,
        reviewTime = reviewTime,
        grade = grade,
        algorithm = algorithm,
        stateBefore = stateBefore,
        stateAfter = stateAfter,
        scheduledDays = scheduledDays,
        elapsedDays = elapsedDays
    )

    private fun makeCard(id: Long, question: String, answer: String = "Answer") = Card(
        id = id,
        question = question,
        answer = answer,
        algorithm = AlgorithmType.FSRS
    )

    // ==================== parseReviewHistory TESTS ====================

    @Test
    fun `parseReviewHistory - returns empty list when no history section`() {
        val lines = listOf(
            "#FSR_EXPORT_V3",
            "#Question\tAnswer\t...",
            "What is a lunge?\tA forward attack"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseReviewHistory - parses single log entry`() {
        val lines = listOf(
            "#FSR_EXPORT_V3",
            "#REVIEW_HISTORY_START",
            "#CardQuestion\tReviewTime\tGrade\tAlgorithm\tStateBefore\tStateAfter\tScheduledDays\tElapsedDays",
            "What is a lunge?\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0",
            "#REVIEW_HISTORY_END"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertEquals(1, result.size)
        val log = result[0]
        assertEquals("What is a lunge?", log.cardQuestion)
        assertEquals(1000L, log.reviewTime)
        assertEquals(3, log.grade)
        assertEquals("FSRS", log.algorithm)
        assertEquals("NEW", log.stateBefore)
        assertEquals("LEARNING", log.stateAfter)
        assertEquals(1, log.scheduledDays)
        assertEquals(0, log.elapsedDays)
    }

    @Test
    fun `parseReviewHistory - parses multiple log entries`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "#CardQuestion\tReviewTime\tGrade\tAlgorithm\tStateBefore\tStateAfter\tScheduledDays\tElapsedDays",
            "Card One\t1000\t2\tFSRS\tNEW\tLEARNING\t1\t0",
            "Card Two\t2000\t4\tSM2\tLEARNING\tREVIEW\t3\t1",
            "#REVIEW_HISTORY_END"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertEquals(2, result.size)
        assertEquals("Card One", result[0].cardQuestion)
        assertEquals(2, result[0].grade)
        assertEquals("Card Two", result[1].cardQuestion)
        assertEquals(4, result[1].grade)
    }

    @Test
    fun `parseReviewHistory - skips comment lines inside section`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "#CardQuestion\tReviewTime\tGrade\tAlgorithm\tStateBefore\tStateAfter\tScheduledDays\tElapsedDays",
            "Card One\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0",
            "#REVIEW_HISTORY_END"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertEquals(1, result.size)
        assertEquals("Card One", result[0].cardQuestion)
    }

    @Test
    fun `parseReviewHistory - skips malformed lines without crashing`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "too\tfew\tcolumns",
            "Card One\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0",
            "#REVIEW_HISTORY_END"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertEquals(1, result.size)
        assertEquals("Card One", result[0].cardQuestion)
    }

    @Test
    fun `parseReviewHistory - handles newline-escaped question text`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "Line one<br>Line two\t5000\t1\tFSRS\tNEW\tLEARNING\t1\t0",
            "#REVIEW_HISTORY_END"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertEquals(1, result.size)
        assertEquals("Line one\nLine two", result[0].cardQuestion)
    }

    @Test
    fun `parseReviewHistory - reads until end of file if no end marker`() {
        val lines = listOf(
            "#REVIEW_HISTORY_START",
            "Card One\t1000\t3\tFSRS\tNEW\tLEARNING\t1\t0"
        )
        val result = CardImportExport.parseReviewHistory(lines)
        assertEquals(1, result.size)
    }

    // ==================== parsedReviewLogsToEntities TESTS ====================

    @Test
    fun `parsedReviewLogsToEntities - maps question to cardId correctly`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "What is a lunge?",
                reviewTime = 1000L,
                grade = 3,
                algorithm = "FSRS",
                stateBefore = "NEW",
                stateAfter = "LEARNING",
                scheduledDays = 1,
                elapsedDays = 0
            )
        )
        val questionToCardId = mapOf("What is a lunge?" to 42L)
        val result = CardImportExport.parsedReviewLogsToEntities(parsed, questionToCardId)
        assertEquals(1, result.size)
        assertEquals(42L, result[0].cardId)
        assertEquals(1000L, result[0].reviewTime)
        assertEquals(3, result[0].grade)
        assertEquals("FSRS", result[0].algorithm)
    }

    @Test
    fun `parsedReviewLogsToEntities - skips logs for unknown cards`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog(
                cardQuestion = "Unknown Card",
                reviewTime = 1000L,
                grade = 3,
                algorithm = "FSRS",
                stateBefore = "NEW",
                stateAfter = "LEARNING",
                scheduledDays = 1,
                elapsedDays = 0
            )
        )
        val result = CardImportExport.parsedReviewLogsToEntities(parsed, emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsedReviewLogsToEntities - handles mixed known and unknown cards`() {
        val parsed = listOf(
            CardImportExport.ParsedReviewLog("Known Card", 1000L, 3, "FSRS", "NEW", "LEARNING", 1, 0),
            CardImportExport.ParsedReviewLog("Unknown Card", 2000L, 2, "FSRS", "LEARNING", "REVIEW", 3, 1)
        )
        val questionToCardId = mapOf("Known Card" to 10L)
        val result = CardImportExport.parsedReviewLogsToEntities(parsed, questionToCardId)
        assertEquals(1, result.size)
        assertEquals(10L, result[0].cardId)
    }

    @Test
    fun `parsedReviewLogsToEntities - returns empty for empty input`() {
        val result = CardImportExport.parsedReviewLogsToEntities(emptyList(), mapOf("Card" to 1L))
        assertTrue(result.isEmpty())
    }

    // ==================== exportCardsWithGroupStates with review history TESTS ====================

    @Test
    fun `exportCardsWithGroupStates - includes REVIEW_HISTORY section when logs provided`() {
        val card = makeCard(1L, "What is a lunge?")
        val cardsWithStates = listOf(CardWithGroupStates(card, emptyList(), emptyMap()))
        val reviewLogs = listOf(makeReviewLog(cardId = 1L))
        val cardQuestions = mapOf(1L to "What is a lunge?")

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            cardsWithStates, output,
            reviewLogs = reviewLogs,
            cardQuestions = cardQuestions
        )
        val content = output.toString(Charsets.UTF_8.name())

        assertTrue(content.contains("#REVIEW_HISTORY_START"))
        assertTrue(content.contains("#REVIEW_HISTORY_END"))
        assertTrue(content.contains("What is a lunge?"))
    }

    @Test
    fun `exportCardsWithGroupStates - omits REVIEW_HISTORY section when no logs`() {
        val card = makeCard(1L, "What is a lunge?")
        val cardsWithStates = listOf(CardWithGroupStates(card, emptyList(), emptyMap()))

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(cardsWithStates, output)
        val content = output.toString(Charsets.UTF_8.name())

        assertFalse(content.contains("#REVIEW_HISTORY_START"))
    }

    @Test
    fun `exportCardsWithGroupStates - review log fields are written correctly`() {
        val card = makeCard(1L, "Parry question")
        val cardsWithStates = listOf(CardWithGroupStates(card, emptyList(), emptyMap()))
        val log = makeReviewLog(
            cardId = 1L,
            grade = 4,
            reviewTime = 99999L,
            algorithm = "SM2",
            stateBefore = "S:1.0,D:5.0,ST:LEARNING",
            stateAfter = "S:2.0,D:5.0,ST:REVIEW",
            scheduledDays = 7,
            elapsedDays = 3
        )
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            cardsWithStates, output,
            reviewLogs = listOf(log),
            cardQuestions = mapOf(1L to "Parry question")
        )
        val content = output.toString(Charsets.UTF_8.name())

        assertTrue(content.contains("Parry question"))
        assertTrue(content.contains("99999"))
        assertTrue(content.contains("4"))
        assertTrue(content.contains("SM2"))
        assertTrue(content.contains("7"))
        assertTrue(content.contains("3"))
    }

    // ==================== round-trip TESTS ====================

    @Test
    fun `round-trip - parseCards populates lastParsedReviewHistory from V3 export`() {
        val card = makeCard(1L, "What is a feint?")
        val cardsWithStates = listOf(CardWithGroupStates(card, emptyList(), emptyMap()))
        val log = makeReviewLog(cardId = 1L, grade = 2, reviewTime = 12345L)

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            cardsWithStates, output,
            reviewLogs = listOf(log),
            cardQuestions = mapOf(1L to "What is a feint?")
        )

        val input = ByteArrayInputStream(output.toByteArray())
        val (parsedCards, errors) = CardImportExport.parseCards(input)

        assertEquals(0, errors.size)
        assertEquals(1, parsedCards.size)
        assertEquals(1, CardImportExport.lastParsedReviewHistory.size)

        val parsedLog = CardImportExport.lastParsedReviewHistory[0]
        assertEquals("What is a feint?", parsedLog.cardQuestion)
        assertEquals(12345L, parsedLog.reviewTime)
        assertEquals(2, parsedLog.grade)
        assertEquals("FSRS", parsedLog.algorithm)
    }

    @Test
    fun `round-trip - parseCards sets empty lastParsedReviewHistory when no history section`() {
        val card = makeCard(1L, "What is a feint?")
        val cardsWithStates = listOf(CardWithGroupStates(card, emptyList(), emptyMap()))

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(cardsWithStates, output)

        val input = ByteArrayInputStream(output.toByteArray())
        CardImportExport.parseCards(input)

        assertTrue(CardImportExport.lastParsedReviewHistory.isEmpty())
    }

    @Test
    fun `round-trip - parsedReviewLogsToEntities restores full ReviewLog after export and parse`() {
        val cardId = 5L
        val cardQuestion = "What is a fleche?"
        val originalLog = makeReviewLog(
            cardId = cardId,
            grade = 1,
            reviewTime = 54321L,
            algorithm = "SM2",
            stateBefore = "EF:2.5,I:1,R:0",
            stateAfter = "EF:2.3,I:1,R:1",
            scheduledDays = 1,
            elapsedDays = 0
        )

        val card = makeCard(cardId, cardQuestion)
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, emptyList(), emptyMap())),
            output,
            reviewLogs = listOf(originalLog),
            cardQuestions = mapOf(cardId to cardQuestion)
        )

        val input = ByteArrayInputStream(output.toByteArray())
        CardImportExport.parseCards(input)

        val entities = CardImportExport.parsedReviewLogsToEntities(
            CardImportExport.lastParsedReviewHistory,
            mapOf(cardQuestion to cardId)
        )

        assertEquals(1, entities.size)
        val restored = entities[0]
        assertEquals(cardId, restored.cardId)
        assertEquals(54321L, restored.reviewTime)
        assertEquals(1, restored.grade)
        assertEquals("SM2", restored.algorithm)
        assertEquals("EF:2.5,I:1,R:0", restored.stateBefore)
        assertEquals("EF:2.3,I:1,R:1", restored.stateAfter)
        assertEquals(1, restored.scheduledDays)
        assertEquals(0, restored.elapsedDays)
        assertNull(restored.sessionId)
    }

    @Test
    fun `round-trip - multiple logs for the same card are all restored`() {
        val cardId = 7L
        val cardQuestion = "What is a parry?"
        val logs = listOf(
            makeReviewLog(cardId, grade = 2, reviewTime = 1000L),
            makeReviewLog(cardId, grade = 3, reviewTime = 2000L),
            makeReviewLog(cardId, grade = 4, reviewTime = 3000L)
        )

        val card = makeCard(cardId, cardQuestion)
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, emptyList(), emptyMap())),
            output,
            reviewLogs = logs,
            cardQuestions = mapOf(cardId to cardQuestion)
        )

        val input = ByteArrayInputStream(output.toByteArray())
        CardImportExport.parseCards(input)

        val entities = CardImportExport.parsedReviewLogsToEntities(
            CardImportExport.lastParsedReviewHistory,
            mapOf(cardQuestion to cardId)
        )

        assertEquals(3, entities.size)
        val grades = entities.map { it.grade }.sorted()
        assertEquals(listOf(2, 3, 4), grades)
    }
}
