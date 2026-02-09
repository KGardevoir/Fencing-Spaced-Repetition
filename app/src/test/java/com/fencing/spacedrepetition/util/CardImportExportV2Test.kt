package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
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

    @Test
    fun `V2 header is correctly detected`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	Group1
        """.trimIndent()

        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertTrue(cards[0].hasFullState)
        assertEquals("GLOBAL", cards[0].stateContext)
    }

    @Test
    fun `V1 format is backward compatible when V2 header not present`() {
        val input = """
            #FSR_EXPORT_V1
            #Headers...
            Q1	A1	FSRS	0	0	0	0	NEW	0	0	0	0	2.5	0	0	Group1
        """.trimIndent()

        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        // V1 format should default to GLOBAL state context
        assertEquals("GLOBAL", cards[0].stateContext)
        assertTrue(cards[0].isGlobalState)
    }

    // ==================== STATE CONTEXT PARSING TESTS ====================

    @Test
    fun `GLOBAL state context is parsed correctly`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	FSRS	GLOBAL	1700000000000	1699900000000	5.0	0.3	REVIEW	3	0	7	5	2.5	0	0	Group1|Group2
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertTrue(cards[0].isGlobalState)
        assertFalse(cards[0].isGroupSpecificState)
        assertEquals("GLOBAL", cards[0].stateContext)
        assertEquals(5.0, cards[0].fsrsStability!!, 0.001)
    }

    @Test
    fun `Group-specific state context is parsed correctly`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	FSRS	MyStudyGroup	1700000000000	1699900000000	10.0	0.2	REVIEW	6	1	14	10	2.5	0	0	MyStudyGroup
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertFalse(cards[0].isGlobalState)
        assertTrue(cards[0].isGroupSpecificState)
        assertEquals("MyStudyGroup", cards[0].stateContext)
        assertEquals(10.0, cards[0].fsrsStability!!, 0.001)
        assertEquals(6, cards[0].fsrsReps)
    }

    @Test
    fun `Multiple state rows for same card are parsed as separate entries`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            What is 2+2?	4	FSRS	GLOBAL	1700000000000	1699900000000	5.0	0.3	REVIEW	3	0	7	5	2.5	0	0	Math|IndependentGroup
            What is 2+2?	4	FSRS	IndependentGroup	1700100000000	1699950000000	8.0	0.25	REVIEW	5	0	10	7	2.5	0	0	IndependentGroup
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)

        // Both rows have the same question/answer
        assertTrue(cards.all { it.question == "What is 2+2?" })
        assertTrue(cards.all { it.answer == "4" })

        // But different state contexts
        val global = cards.find { it.isGlobalState }!!
        val groupSpecific = cards.find { it.isGroupSpecificState }!!

        assertEquals(5.0, global.fsrsStability!!, 0.001)
        assertEquals(3, global.fsrsReps)

        assertEquals(8.0, groupSpecific.fsrsStability!!, 0.001)
        assertEquals(5, groupSpecific.fsrsReps)
        assertEquals("IndependentGroup", groupSpecific.stateContext)
    }

    // ==================== COMPLEX SCENARIO TESTS ====================

    @Test
    fun `Card with multiple independent learning groups`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Question	Answer	FSRS	GLOBAL	0	0	1.0	0.5	NEW	0	0	0	0	2.5	0	0	Group1|Group2|Group3
            Question	Answer	FSRS	Group1	0	0	2.0	0.4	LEARNING	1	0	1	0	2.5	0	0	Group1
            Question	Answer	FSRS	Group2	0	0	3.0	0.3	LEARNING	2	0	2	1	2.5	0	0	Group2
            Question	Answer	FSRS	Group3	0	0	4.0	0.2	REVIEW	3	0	3	2	2.5	0	0	Group3
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(4, cards.size)

        // One global, three group-specific
        val globalCards = cards.filter { it.isGlobalState }
        val groupCards = cards.filter { it.isGroupSpecificState }

        assertEquals(1, globalCards.size)
        assertEquals(3, groupCards.size)

        // Verify each group has different progress
        val group1 = groupCards.find { it.stateContext == "Group1" }!!
        val group2 = groupCards.find { it.stateContext == "Group2" }!!
        val group3 = groupCards.find { it.stateContext == "Group3" }!!

        assertEquals(2.0, group1.fsrsStability!!, 0.001)
        assertEquals(3.0, group2.fsrsStability!!, 0.001)
        assertEquals(4.0, group3.fsrsStability!!, 0.001)
    }

    @Test
    fun `Mixed cards - some with independent learning, some without`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Card1	Answer1	FSRS	GLOBAL	0	0	1.0	0.5	NEW	0	0	0	0	2.5	0	0	Group1
            Card2	Answer2	FSRS	GLOBAL	0	0	2.0	0.4	LEARNING	1	0	1	0	2.5	0	0	Group1|IndependentGroup
            Card2	Answer2	FSRS	IndependentGroup	0	0	5.0	0.2	REVIEW	3	0	5	3	2.5	0	0	IndependentGroup
            Card3	Answer3	FSRS	GLOBAL	0	0	3.0	0.3	REVIEW	2	0	3	2	2.5	0	0	AnotherGroup
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(4, cards.size)

        // Card1 and Card3 only have global state
        val card1Cards = cards.filter { it.question == "Card1" }
        val card3Cards = cards.filter { it.question == "Card3" }
        assertEquals(1, card1Cards.size)
        assertEquals(1, card3Cards.size)
        assertTrue(card1Cards[0].isGlobalState)
        assertTrue(card3Cards[0].isGlobalState)

        // Card2 has global + group-specific
        val card2Cards = cards.filter { it.question == "Card2" }
        assertEquals(2, card2Cards.size)
        assertEquals(1, card2Cards.count { it.isGlobalState })
        assertEquals(1, card2Cards.count { it.isGroupSpecificState })
    }

    // ==================== EXPORT FORMAT TESTS ====================
    // Note: exportCardsWithGroupStates uses V3 format (the latest),
    // but V2 import parsing remains backward-compatible.

    @Test
    fun `Export with no independent learning groups`() {
        val card = Card(
            id = 1,
            question = "Test Question",
            answer = "Test Answer",
            algorithm = AlgorithmType.FSRS,
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
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(content.startsWith("#FSR_EXPORT_V3"))
        assertTrue(content.contains("\tGLOBAL\t"))

        // Should only have header + column names + 1 data row
        val dataLines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(1, dataLines.size)
    }

    @Test
    fun `Export with one independent learning group`() {
        val card = Card(
            id = 1,
            question = "Test Question",
            answer = "Test Answer",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 5.0,
            fsrsDifficulty = 0.3,
            fsrsState = "REVIEW",
            fsrsReps = 3,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val groupState = CardGroupLearningState(
            cardId = 1,
            groupId = 2,
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
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output)

        assertTrue(result is ExportResult.Success)
        assertEquals(2, (result as ExportResult.Success).exportedCount) // 1 global + 1 group-specific

        val content = output.toString(Charsets.UTF_8.name())
        val dataLines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(2, dataLines.size)

        // First line should be global
        assertTrue(dataLines[0].contains("\tGLOBAL\t"))
        assertTrue(dataLines[0].contains("5.0")) // Global stability

        // Second line should be group-specific
        assertTrue(dataLines[1].contains("\tIndependentGroup\t"))
        assertTrue(dataLines[1].contains("10.0")) // Group stability
    }

    @Test
    fun `Export with multiple independent learning groups`() {
        val card = Card(
            id = 1,
            question = "Test",
            answer = "Answer",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 1.0,
            fsrsReps = 1,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val group1State = CardGroupLearningState(cardId = 1, groupId = 1, fsrsStability = 2.0, fsrsReps = 2)
        val group2State = CardGroupLearningState(cardId = 1, groupId = 2, fsrsStability = 3.0, fsrsReps = 3)
        val group3State = CardGroupLearningState(cardId = 1, groupId = 3, fsrsStability = 4.0, fsrsReps = 4)

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
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output)

        assertTrue(result is ExportResult.Success)
        assertEquals(4, (result as ExportResult.Success).exportedCount) // 1 global + 3 group-specific

        val content = output.toString(Charsets.UTF_8.name())
        val dataLines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(4, dataLines.size)
    }

    // ==================== ROUND-TRIP FORMAT TESTS ====================

    @Test
    fun `Round-trip export and import preserves all data`() {
        val originalCard = Card(
            id = 1,
            question = "Round trip V2 test",
            answer = "Answer with<br>newlines",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 5.5,
            fsrsDifficulty = 0.35,
            fsrsState = "REVIEW",
            fsrsReps = 4,
            fsrsLapses = 1,
            fsrsScheduledDays = 10,
            fsrsElapsedDays = 7,
            sm2EaseFactor = 2.6,
            sm2Interval = 14,
            sm2Repetitions = 5,
            nextReview = 1700000000000,
            lastReview = 1699900000000,
            created = System.currentTimeMillis(),
            modified = System.currentTimeMillis()
        )

        val groupState = CardGroupLearningState(
            cardId = 1,
            groupId = 2,
            fsrsStability = 12.0,
            fsrsDifficulty = 0.2,
            fsrsState = "REVIEW",
            fsrsReps = 8,
            fsrsLapses = 0,
            fsrsScheduledDays = 20,
            fsrsElapsedDays = 14,
            sm2EaseFactor = 2.7,
            sm2Interval = 21,
            sm2Repetitions = 7,
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
        CardImportExport.exportCardsWithGroupStates(original, output)

        // Import
        val (parsed, errors) = CardImportExport.parseCards(ByteArrayInputStream(output.toByteArray()))

        assertEquals(0, errors.size)
        assertEquals(2, parsed.size)

        // Verify global state
        val globalParsed = parsed.find { it.isGlobalState }!!
        assertEquals(originalCard.question, globalParsed.question)
        assertEquals("Answer with\nnewlines", globalParsed.answer) // Newlines unescaped
        assertEquals(originalCard.algorithm, globalParsed.algorithm)
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

    @Test
    fun `Identify which groups have independent learning from import`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	GroupA|GroupB|GroupC
            Q1	A1	FSRS	GroupB	0	0	1.0	0.1	LEARNING	1	0	1	0	2.5	0	0	GroupB
            Q2	A2	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	GroupA|GroupC
            Q2	A2	FSRS	GroupC	0	0	2.0	0.2	REVIEW	2	0	2	1	2.5	0	0	GroupC
            Q3	A3	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	GroupA
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        // Extract groups that have independent learning (appear as stateContext)
        val independentLearningGroups = cards
            .filter { it.isGroupSpecificState }
            .mapNotNull { it.stateContext }
            .toSet()

        assertEquals(setOf("GroupB", "GroupC"), independentLearningGroups)

        // GroupA never appears as a stateContext, so it doesn't have independent learning
        assertFalse("GroupA" in independentLearningGroups)
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `Empty groups list in V2 format`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertTrue(cards[0].groupNames.isEmpty())
    }

    @Test
    fun `Group name with spaces`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	FSRS	My Study Group	0	0	5.0	0.3	REVIEW	3	0	5	3	2.5	0	0	My Study Group
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertTrue(cards[0].isGroupSpecificState)
        assertEquals("My Study Group", cards[0].stateContext)
    }

    @Test
    fun `V2 format with SM2 algorithm`() {
        val input = """
            #FSR_EXPORT_V2
            #Headers...
            Q1	A1	SM2	GLOBAL	1700000000000	1699900000000	0.0	0.0	NEW	0	0	0	0	2.8	14	5	Study
            Q1	A1	SM2	Study	1700100000000	1700000000000	0.0	0.0	NEW	0	0	0	0	3.0	21	8	Study
        """.trimIndent()

        val (cards, _) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(2, cards.size)
        assertTrue(cards.all { it.algorithm == AlgorithmType.SM2 })

        val global = cards.find { it.isGlobalState }!!
        val groupSpecific = cards.find { it.isGroupSpecificState }!!

        assertEquals(2.8, global.sm2EaseFactor!!, 0.001)
        assertEquals(14, global.sm2Interval)
        assertEquals(5, global.sm2Repetitions)

        assertEquals(3.0, groupSpecific.sm2EaseFactor!!, 0.001)
        assertEquals(21, groupSpecific.sm2Interval)
        assertEquals(8, groupSpecific.sm2Repetitions)
    }

    @Test
    fun `Export multiple cards with varying independent learning states`() {
        val card1 = Card(id = 1, question = "Q1", answer = "A1", algorithm = AlgorithmType.FSRS,
            fsrsStability = 1.0, created = 0, modified = 0)
        val card2 = Card(id = 2, question = "Q2", answer = "A2", algorithm = AlgorithmType.FSRS,
            fsrsStability = 2.0, created = 0, modified = 0)
        val card3 = Card(id = 3, question = "Q3", answer = "A3", algorithm = AlgorithmType.FSRS,
            fsrsStability = 3.0, created = 0, modified = 0)

        val groupState1 = CardGroupLearningState(cardId = 1, groupId = 1, fsrsStability = 5.0)
        val groupState2 = CardGroupLearningState(cardId = 2, groupId = 1, fsrsStability = 6.0)

        val cardsWithStates = listOf(
            CardWithGroupStates(card1, listOf("IndGroup"), mapOf("IndGroup" to groupState1)),
            CardWithGroupStates(card2, listOf("IndGroup"), mapOf("IndGroup" to groupState2)),
            CardWithGroupStates(card3, listOf("NormalGroup"), emptyMap())
        )

        val output = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, output)

        assertTrue(result is ExportResult.Success)
        // Card1: 1 global + 1 group = 2
        // Card2: 1 global + 1 group = 2
        // Card3: 1 global = 1
        // Total = 5
        assertEquals(5, (result as ExportResult.Success).exportedCount)

        val content = output.toString(Charsets.UTF_8.name())
        val dataLines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(5, dataLines.size)
    }
}
