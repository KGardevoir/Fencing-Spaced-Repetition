// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.LearningGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stable identifiers and reset times survive an archive export and import. */
class IdentityArchiveTest {

    private val noImages = ImageReader { null }

    @Test
    fun identifiersAndResetTimesRoundTrip() {
        val card = Card(id = 1, question = "Lunge", answer = "", lastReset = 1234L, fsrsReps = 2, fsrsState = "REVIEW")
        val lg = LearningGroup(id = 5, name = "Bouting")
        val out = StringBuilder()
        CardImportExport.exportCardsWithGroupStates(
            listOf(
                CardWithGroupStates(
                    card, listOf("Footwork"),
                    mapOf("Bouting" to com.fencing.spacedrepetition.data.model.CardLearningState(1, 5, fsrsReps = 1, lastReset = 99L))
                )
            ),
            out, noImages,
            groupSettings = listOf(com.fencing.spacedrepetition.data.model.Group(id = 3, name = "Footwork", learningGroupId = 5)),
            learningGroups = listOf(lg)
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
