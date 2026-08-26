// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.ReviewLog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the photo export names what it packs.
 *
 * All of this exists because stored keys are content hashes. An archive that
 * used them would be correct and useless -- sixty-four hex digits per file --
 * so the names come from the cards, and everything awkward about that
 * (repeats, collisions, a photo in two places at once) is what these pin down.
 */
class PhotoArchiveTest {

    private val reader = ImageReader { key -> bytesFor(key) }

    @Test
    fun namesACardsPhotoAfterTheCard() {
        val entries = photoArchiveEntries(
            cards = listOf(card(1, "Sixte parry", listOf("a.jpg"))),
            reviewLogs = emptyList(),
            images = reader
        )

        assertEquals(listOf("cards/Sixte_parry.jpg"), entries.map { it.name })
    }

    @Test
    fun numbersTheSecondAndLaterPhotosOfACard() {
        val entries = photoArchiveEntries(
            cards = listOf(card(1, "Sixte parry", listOf("a.jpg", "b.jpg", "c.jpg"))),
            reviewLogs = emptyList(),
            images = reader
        )

        assertEquals(
            listOf("cards/Sixte_parry.jpg", "cards/Sixte_parry_2.jpg", "cards/Sixte_parry_3.jpg"),
            entries.map { it.name }
        )
    }

    @Test
    fun separatesTwoCardsThatShareAName() {
        val entries = photoArchiveEntries(
            cards = listOf(
                card(1, "Parry", listOf("a.jpg")),
                card(2, "Parry", listOf("b.jpg"))
            ),
            reviewLogs = emptyList(),
            images = reader
        )

        assertEquals(listOf("cards/Parry.jpg", "cards/Parry_2.jpg"), entries.map { it.name })
    }

    @Test
    fun putsNotePhotosUnderReviewsNamedForTheirCard() {
        val entries = photoArchiveEntries(
            cards = listOf(card(4, "Sixte parry", emptyList())),
            reviewLogs = listOf(reviewLog(4, "a.jpg,b.jpg")),
            images = reader
        )

        assertEquals(
            listOf("reviews/Sixte_parry.jpg", "reviews/Sixte_parry_2.jpg"),
            entries.map { it.name }
        )
    }

    /** The store keeps one file per content, so one file is what comes out. */
    @Test
    fun packsAPhotoOnceEvenWhenTwoThingsPointAtIt() {
        val entries = photoArchiveEntries(
            cards = listOf(
                card(1, "Sixte parry", listOf("shared.jpg")),
                card(2, "Quarte parry", listOf("shared.jpg"))
            ),
            reviewLogs = listOf(reviewLog(1, "shared.jpg")),
            images = reader
        )

        assertEquals(listOf("cards/Sixte_parry.jpg"), entries.map { it.name })
    }

    @Test
    fun keepsTheExtensionTheStoreRecorded() {
        val entries = photoArchiveEntries(
            cards = listOf(card(1, "Diagram", listOf("a.png"))),
            reviewLogs = emptyList(),
            images = reader
        )

        assertEquals(listOf("cards/Diagram.png"), entries.map { it.name })
    }

    @Test
    fun leavesOutAPhotoThatWillNotRead() {
        val entries = photoArchiveEntries(
            cards = listOf(card(1, "Sixte parry", listOf("a.jpg", "gone.jpg"))),
            reviewLogs = emptyList(),
            images = ImageReader { key -> if (key == "gone.jpg") null else bytesFor(key) }
        )

        assertEquals(listOf("cards/Sixte_parry.jpg"), entries.map { it.name })
    }

    @Test
    fun fallsBackToANameWhenTheQuestionLeavesNothingBehind() {
        val entries = photoArchiveEntries(
            cards = listOf(card(1, "", listOf("a.jpg"))),
            reviewLogs = emptyList(),
            images = reader
        )

        // An entry with no name at all is one some unpackers refuse, so a
        // card with nothing to name it after still gets a name.
        assertEquals(listOf("cards/card.jpg"), entries.map { it.name })
    }

    @Test
    fun carriesTheBytesItWasGiven() {
        val entries = photoArchiveEntries(
            cards = listOf(card(1, "Sixte parry", listOf("a.jpg"))),
            reviewLogs = emptyList(),
            images = reader
        )

        assertEquals(bytesFor("a.jpg").toList(), entries.single().bytes.toList())
    }

    private fun bytesFor(key: String) = key.encodeToByteArray()

    private fun card(id: Long, question: String, imagePaths: List<String>) =
        Card(id = id, question = question, answer = "", imagePaths = imagePaths)

    private fun reviewLog(cardId: Long, imagePaths: String) =
        ReviewLog(
            cardId = cardId,
            sessionId = null,
            reviewTime = 1_700_000_000_000,
            grade = 3,
            algorithm = "FSRS",
            stateBefore = "NEW",
            stateAfter = "REVIEW",
            scheduledDays = 1,
            elapsedDays = 0,
            imagePaths = imagePaths
        )
}
