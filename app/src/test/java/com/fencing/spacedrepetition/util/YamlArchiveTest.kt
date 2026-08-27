// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import org.junit.Assert.*
import org.junit.Test

/**
 * The YAML document an export writes, and what an import makes of one.
 *
 * The sibling files cover the pieces -- group settings, opponents, review
 * history -- against the format they were written for. This one is about the
 * document as a whole: what it looks like, that everything in it survives a
 * round trip, that a file someone typed by hand imports, and that a file in
 * the tab-separated format it replaced still does.
 */
class YamlArchiveTest {

    // ==================== the document ====================

    @Test
    fun `an export is a YAML document with the sections in a fixed order`() {
        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            cardsWithStates = listOf(
                CardWithGroupStates(
                    Card(id = 1, question = "Parry four", answer = "Inside line", created = 0, modified = 0),
                    listOf("Footwork"),
                    mapOf("Footwork" to CardGroupLearningState(cardId = 1, groupId = 9, fsrsReps = 2))
                )
            ),
            out = out,
            images = NoImages,
            groupSettings = listOf(Group(id = 9, name = "Footwork", cardsPerSession = 20)),
            reviewLogs = listOf(reviewLog(cardId = 1)),
            cardQuestions = mapOf(1L to "Parry four"),
            opponents = listOf(Opponent(id = 3, name = "Alex", skillMultiplier = 1.25, created = 0, modified = 0)),
            opponentNamesById = mapOf(3L to "Alex")
        )
        val content = out.toString()

        assertTrue(content.startsWith("# Fencing Spaced Repetition export\nversion: 5\n"))
        assertEquals(
            listOf("groups", "opponents", "cards", "reviewHistory"),
            Regex("^([a-zA-Z]+):$", RegexOption.MULTILINE).findAll(content)
                .map { it.groupValues[1] }
                .toList()
        )
    }

    @Test
    fun `a card's pictures are written once, however many groups learn it`() {
        val image = ByteArray(300) { it.toByte() }
        val card = Card(
            id = 1, question = "Q", answer = "A", imagePaths = listOf("photo.jpg"),
            created = 0, modified = 0
        )
        val states = (1..3).associate { "Group$it" to CardGroupLearningState(cardId = 1, groupId = it.toLong()) }

        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, states.keys.toList(), states)),
            out,
            images = { path -> if (path == "photo.jpg") image else null }
        )
        val content = out.toString()

        val encoded = java.util.Base64.getEncoder().encodeToString(image)
        assertEquals(1, content.windowed(encoded.length) { it }.count { it == encoded })

        // And it still comes back attached to the card.
        val (parsed, errors) = CardImportExport.parseCards(content.lines())
        assertEquals(emptyList<String>(), errors)
        val global = parsed.first { it.isGlobalState }
        assertEquals(1, global.imageData.size)
        assertArrayEquals(image, CardImportExport.decodeBase64Image(global.imageData[0]))
    }

    @Test
    fun `everything an archive carries survives the round trip`() {
        val card = Card(
            id = 1, question = "Parry: four", answer = "Inside line,\nhand high",
            fsrsStability = 12.5, fsrsDifficulty = 5.25, fsrsElapsedDays = 3,
            fsrsScheduledDays = 7, fsrsReps = 4, fsrsLapses = 1, fsrsState = "REVIEW",
            lastReview = 1_699_900_000_000L, nextReview = 1_700_000_000_000L,
            created = 0, modified = 0
        )
        val groupState = CardGroupLearningState(
            cardId = 1, groupId = 9, fsrsStability = 3.5, fsrsDifficulty = 6.0,
            fsrsElapsedDays = 1, fsrsScheduledDays = 2, fsrsReps = 2, fsrsLapses = 0,
            fsrsState = "LEARNING", lastReview = 100L, nextReview = 200L
        )
        val group = Group(
            id = 9, name = "Foot work", cardsPerSession = 20, autoShowAnswer = true,
            randomizeDueCards = false, randomizeBucketHours = 6, practiceDays = "1,3,5",
            maximumInterval = 365, fsrsRetention = 85, fsrsEnableFuzzing = false,
            created = 0
        )
        val log = reviewLog(
            cardId = 1, grade = 2, notes = "Late:\nhand dropped", groupName = "Foot work",
            opponentId = 3, stabilityMultiplier = 1.25
        )

        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            cardsWithStates = listOf(CardWithGroupStates(card, listOf("Foot work"), mapOf("Foot work" to groupState))),
            out = out,
            images = NoImages,
            groupSettings = listOf(group),
            reviewLogs = listOf(log),
            cardQuestions = mapOf(1L to card.question),
            opponents = listOf(Opponent(id = 3, name = "Alex", skillMultiplier = 1.25, notes = "Left-handed", created = 0, modified = 0)),
            opponentNamesById = mapOf(3L to "Alex")
        )

        val (parsed, errors) = CardImportExport.parseCards(out.toString().lines())
        assertEquals(emptyList<String>(), errors)
        assertEquals(2, parsed.size)

        val global = parsed.first { it.isGlobalState }
        assertEquals(card.question, global.concept)
        assertEquals(card.answer, global.answer)
        assertEquals(card.fsrsStability, global.fsrsStability!!, 0.0001)
        assertEquals(card.fsrsDifficulty, global.fsrsDifficulty!!, 0.0001)
        assertEquals(card.fsrsState, global.fsrsState)
        assertEquals(card.fsrsReps, global.fsrsReps)
        assertEquals(card.fsrsLapses, global.fsrsLapses)
        assertEquals(card.fsrsScheduledDays, global.fsrsScheduledDays)
        assertEquals(card.fsrsElapsedDays, global.fsrsElapsedDays)
        assertEquals(card.nextReview, global.nextReview)
        assertEquals(card.lastReview, global.lastReview)
        assertEquals(listOf("Foot work"), global.groupNames)

        val perGroup = parsed.first { it.isGroupSpecificState }
        assertEquals("Foot work", perGroup.stateContext)
        assertEquals(listOf("Foot work"), perGroup.groupNames)
        assertEquals(groupState.fsrsStability, perGroup.fsrsStability!!, 0.0001)
        assertEquals(groupState.fsrsState, perGroup.fsrsState)
        assertEquals(groupState.nextReview, perGroup.nextReview)

        val restored = CardImportExport.applyGroupSettings(
            Group(id = 0, name = "Foot work", created = 0),
            CardImportExport.lastParsedGroupSettings.getValue("Foot work")
        )
        assertEquals(group.copy(id = 0, created = 0), restored)

        val opponent = CardImportExport.lastParsedOpponents.single()
        assertEquals("Alex", opponent.name)
        assertEquals(1.25, opponent.skillMultiplier, 0.0001)
        assertEquals("Left-handed", opponent.notes)

        val history = CardImportExport.lastParsedReviewHistory.single()
        assertEquals(card.question, history.cardQuestion)
        assertEquals(log.reviewTime, history.reviewTime)
        assertEquals(2, history.grade)
        assertEquals("Late:\nhand dropped", history.notes)
        assertEquals("Foot work", history.groupName)
        assertEquals("Alex", history.opponentName)
        assertEquals(1.25, history.stabilityMultiplier, 0.0001)
    }

    /**
     * The characters and words YAML gives a meaning to, run through the real
     * export path rather than through the writer on its own -- because it is
     * the export that decides what a question, an answer and a group name are
     * written as.
     */
    @Test
    fun `awkward content survives the round trip`() {
        val awkward = listOf(
            "plain", " leading", "trailing ", "has: colon", "has # hash", "\"quotes\"",
            "'apostrophes'", "back\\slash", "- dash", "42", "3.14", "true", "no", "null",
            "~", "[list]", "{map}", "*star", "&anchor", "%percent", "@at", "?question",
            "line one\nline two", "é 日本語 🤺", "tab\there"
        )
        awkward.forEachIndexed { index, value ->
            val card = Card(
                id = index.toLong(), question = "q$index", answer = value,
                created = 0, modified = 0
            )
            val out = StringBuilder()
            CardImportExport.exportCardsWithGroups(listOf(CardWithGroupNames(card, listOf(value))), out)

            val (parsed, errors) = CardImportExport.parseCards(out.toString().lines())
            assertEquals("errors for <$value>", emptyList<String>(), errors)
            assertEquals("count for <$value>", 1, parsed.size)
            assertEquals("answer for <$value>", value, parsed[0].answer)
            assertEquals("groups for <$value>", listOf(value), parsed[0].groupNames)
        }
    }

    // ==================== files people write themselves ====================

    @Test
    fun `a hand-written list of questions and answers imports`() {
        val (cards, errors) = CardImportExport.parseCards(
            """
            cards:
              - question: What is a lunge?
                answer: A step and an extension.
              - question: What is a fleche?
                answer: A running attack.
            """.trimIndent().lines()
        )
        assertEquals(emptyList<String>(), errors)
        assertEquals(2, cards.size)
        assertEquals("What is a lunge?", cards[0].concept)
        assertEquals("A running attack.", cards[1].answer)
        assertFalse("nothing to schedule yet", cards[0].hasFullState)
    }

    @Test
    fun `a hand-written file may use the CSV column names`() {
        val (cards, errors) = CardImportExport.parseCards(
            "cards:\n  - concept: Octave\n    description: Outside line, low.".lines()
        )
        assertEquals(emptyList<String>(), errors)
        assertEquals("Octave", cards.single().concept)
        assertEquals("Outside line, low.", cards.single().answer)
    }

    @Test
    fun `a bare list of cards, with no wrapper, imports`() {
        val (cards, errors) = CardImportExport.parseCards(
            "- question: One\n  answer: Two".lines()
        )
        assertEquals(emptyList<String>(), errors)
        assertEquals("One", cards.single().concept)
    }

    /**
     * A card with groups but no scheduling still has to reach the importer as
     * a card that has groups -- the question-and-answer path drops them.
     */
    @Test
    fun `groups on a card with no state are kept`() {
        val (cards, errors) = CardImportExport.parseCards(
            """
            cards:
              - question: Sixte
                answer: Outside line, high.
                groups: [Parries, Foil]
            """.trimIndent().lines()
        )
        assertEquals(emptyList<String>(), errors)
        val card = cards.single()
        assertEquals(listOf("Parries", "Foil"), card.groupNames)
        assertTrue(card.hasFullState)
        assertTrue(card.isGlobalState)
        // Nothing has been practised, so nothing is scheduled: the record
        // carries a state context and no state, and becomes a new card.
        assertNull(card.nextReview)
        assertNull(card.fsrsState)
        assertEquals(0L, CardImportExport.parsedCardToCard(card).nextReview)
        assertEquals("NEW", CardImportExport.parsedCardToCard(card).fsrsState)
    }

    @Test
    fun `a card with no question is one error, and the rest still import`() {
        val (cards, errors) = CardImportExport.parseCards(
            """
            cards:
              - answer: no question
              - question: Fine
                answer: Yes
            """.trimIndent().lines()
        )
        assertEquals(1, errors.size)
        assertTrue(errors[0], errors[0].startsWith("Line 2:"))
        assertEquals("Fine", cards.single().concept)
    }

    /**
     * A card is decoded as one value, so a part of it that will not read
     * costs the whole card -- one error, naming the property and the line,
     * and the rest of the file still imports.
     */
    @Test
    fun `a group state with no group name is one error`() {
        val (cards, errors) = CardImportExport.parseCards(
            """
            cards:
              - question: Q
                answer: A
                groupStates:
                  - reps: 2
              - question: Fine
                answer: Yes
            """.trimIndent().lines()
        )
        assertEquals(1, cards.size)
        assertEquals("Fine", cards.single().concept)
        assertEquals(1, errors.size)
        assertTrue(errors[0], errors[0].startsWith("Line 2:"))
        assertTrue(errors[0], errors[0].contains("group"))
    }

    @Test
    fun `a YAML file that says nothing about cards is refused`() {
        val (cards, errors) = CardImportExport.parseCards("shopping:\n  - milk\n  - eggs".lines())
        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0], errors[0].contains("cards:"))
        assertTrue(errors[0], errors[0].contains("FSR_EXPORT"))
    }

    @Test
    fun `broken YAML is refused with the line it broke on`() {
        val (cards, errors) = CardImportExport.parseCards("cards:\n\t- question: x".lines())
        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0], errors[0].startsWith("Line 2:"))
    }

    // ==================== the format it replaced ====================

    @Test
    fun `a tab-separated export still imports`() {
        val legacy = listOf(
            "#FSR_EXPORT_V4",
            "#GROUP_SETTINGS:Footwork\tcardsPerSession=15",
            "#OPPONENT:Alex\tskillMultiplier=1.5\tnotes=lefty",
            "#Question\tAnswer",
            "Q1\tA1\t\tGLOBAL\t100\t50\t1.5\t5.0\tREVIEW\t2\t0\t3\t1\tFootwork",
            "Q1\tA1\t\tFootwork\t200\t60\t2.5\t6.0\tLEARNING\t1\t0\t1\t1\tFootwork",
            "#REVIEW_HISTORY_START",
            "Q1\t1000\t3\tFSRS\tbefore\tafter\t2\t1\tFootwork\tnotes\t\tAlex\t1.5",
            "#REVIEW_HISTORY_END"
        )
        val (cards, errors) = CardImportExport.parseCards(legacy)

        assertEquals(emptyList<String>(), errors)
        assertEquals(2, cards.size)
        assertEquals(1.5, cards[0].fsrsStability!!, 0.0001)
        assertEquals("Footwork", cards[1].stateContext)
        assertEquals(mapOf("cardsPerSession" to "15"), CardImportExport.lastParsedGroupSettings["Footwork"])
        assertEquals("lefty", CardImportExport.lastParsedOpponents.single().notes)
        assertEquals("Alex", CardImportExport.lastParsedReviewHistory.single().opponentName)
    }

    /**
     * The three `lastParsed` properties outlive the call that filled them, so
     * a second import has to clear what the first left behind -- otherwise a
     * plain deck imported after a backup would quietly acquire the backup's
     * history.
     */
    @Test
    fun `a second import does not inherit the first one's metadata`() {
        CardImportExport.parseCards(
            listOf(
                "#FSR_EXPORT_V4",
                "#GROUP_SETTINGS:Footwork\tcardsPerSession=15",
                "#OPPONENT:Alex\tskillMultiplier=1.5",
                "Q1\tA1",
                "#REVIEW_HISTORY_START",
                "Q1\t1000\t3\tFSRS\tbefore\tafter\t2\t1",
                "#REVIEW_HISTORY_END"
            )
        )
        assertTrue(CardImportExport.lastParsedGroupSettings.isNotEmpty())

        CardImportExport.parseCards("cards:\n  - question: a\n    answer: b".lines())

        assertEquals(emptyMap<String, Map<String, String>>(), CardImportExport.lastParsedGroupSettings)
        assertEquals(emptyList<CardImportExport.ParsedOpponent>(), CardImportExport.lastParsedOpponents)
        assertEquals(emptyList<CardImportExport.ParsedReviewLog>(), CardImportExport.lastParsedReviewHistory)
    }

    @Test
    fun `an archive is named for what it now holds`() {
        assertTrue(CardImportExport.generateAllCardsFilename().endsWith("_all_cards.yaml.gz"))
        assertTrue(CardImportExport.generateSelectedGroupsFilename().endsWith("_selected_groups_cards.yaml.gz"))
        assertTrue(CardImportExport.generateBackupFilename().endsWith("_backup.yaml.gz"))
        assertTrue(CardImportExport.generateExportFilename("Foil").endsWith("_Foil_cards.yaml.gz"))
        assertEquals(".yaml.gz", CardImportExport.ARCHIVE_EXTENSION)
    }

    @Test
    fun `a group name is derived from a yaml filename too`() {
        assertEquals(
            "Parries",
            CardImportExport.deriveGroupNameFromFilename("2026-08-26_14-05-09_parries_cards.yaml.gz")
        )
        assertEquals("Parries", CardImportExport.deriveGroupNameFromFilename("parries.yml"))
    }

    private fun reviewLog(
        cardId: Long,
        grade: Int = 3,
        notes: String = "",
        groupName: String? = null,
        opponentId: Long? = null,
        stabilityMultiplier: Double = 1.0
    ) = ReviewLog(
        id = 0, cardId = cardId, sessionId = null, reviewTime = 1_700_000_000_000L,
        grade = grade, algorithm = "FSRS", stateBefore = "S:1.0", stateAfter = "S:2.0",
        scheduledDays = 2, elapsedDays = 1, groupName = groupName, notes = notes,
        imagePaths = "", opponentId = opponentId, stabilityMultiplier = stabilityMultiplier
    )

    /** Reads no images: these cards have none unless the test says otherwise. */
    private val NoImages = ImageReader { null }
}
