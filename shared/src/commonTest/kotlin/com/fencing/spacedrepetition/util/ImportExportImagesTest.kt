// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.ReviewLog
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What an import does with the pictures inside a file.
 *
 * The property worth pinning down is that an imported image is stored the
 * same way an attached one is: as a content key. Import used to write its own
 * file per image, named after the clock, so re-importing your own export
 * duplicated every photograph in the collection and the rows pointed at
 * absolute paths that no browser could ever resolve. These tests are what
 * says that is over.
 */
class ImportExportImagesTest {

    private val image = byteArrayOf(1, 2, 3, 4, 5)
    private val otherImage = byteArrayOf(9, 9, 9)

    @OptIn(ExperimentalEncodingApi::class)
    private fun encoded(bytes: ByteArray) = Base64.encode(bytes)

    @Test
    fun storesAnImportedImageUnderItsContentKey() = runTest {
        val store = FakeImageStore()

        val keys = CardImportExport.storeImages(listOf(encoded(image)), store)

        assertEquals(listOf(contentKey(image)), keys)
        assertEquals(image.toList(), store.files.getValue(keys.single()).toList())
    }

    /** Nothing about a key says where it is: no separators, no directory. */
    @Test
    fun theStoredKeyIsNotAPath() = runTest {
        val store = FakeImageStore()

        val key = CardImportExport.storeImages(listOf(encoded(image)), store).single()

        assertTrue(!key.contains('/'), "expected a content key, got a path: $key")
    }

    /** The whole point: the same picture twice is one file. */
    @Test
    fun storesTheSameImageOnce() = runTest {
        val store = FakeImageStore()

        val first = CardImportExport.storeImages(listOf(encoded(image)), store)
        val second = CardImportExport.storeImages(listOf(encoded(image), encoded(otherImage)), store)

        assertEquals(first.single(), second.first())
        assertEquals(2, store.files.size)
    }

    /** A card with one unreadable picture is still a card. */
    @Test
    fun skipsImagesThatWillNotDecode() = runTest {
        val store = FakeImageStore()

        val keys = CardImportExport.storeImages(listOf("not base64 at all!", encoded(image)), store)

        assertEquals(listOf(contentKey(image)), keys)
    }

    @Test
    fun aParsedCardKeepsTheKeysOfItsStoredImages() = runTest {
        val store = FakeImageStore()
        val parsed = ParsedCard(
            concept = "Sixte parry",
            answer = "Close the outside high line",
            lineNumber = 2,
            imageData = listOf(encoded(image))
        )

        val card = CardImportExport.parsedCardToCard(parsed, store)

        assertEquals("Sixte parry", card.question)
        assertEquals(listOf(contentKey(image)), card.imagePaths)
    }

    @Test
    fun reviewLogsAreLinkedByQuestionAndKeepTheirImages() = runTest {
        val store = FakeImageStore()
        val logs = listOf(
            parsedLog("Sixte parry", listOf(encoded(image), encoded(otherImage))),
            parsedLog("A card that is not here", listOf(encoded(image)))
        )

        val entities = CardImportExport.parsedReviewLogsToEntities(
            parsed = logs,
            questionToCardId = mapOf("Sixte parry" to 7L),
            images = store
        )

        assertEquals(1, entities.size)
        assertEquals(7L, entities.single().cardId)
        assertEquals(
            "${contentKey(image)},${contentKey(otherImage)}",
            entities.single().imagePaths
        )
    }

    /** What an export has to load before it can format anything. */
    @Test
    fun exportKeysCoverCardsAndReviewLogsWithoutRepeating() = runTest {
        val keys = exportImageKeys(
            cards = listOf(
                card("Sixte parry", listOf("a.jpg", "b.jpg")),
                card("Quarte parry", listOf("a.jpg"))
            ),
            reviewLogs = listOf(
                reviewLog("b.jpg,c.jpg"),
                reviewLog(""),
            )
        )

        assertEquals(setOf("a.jpg", "b.jpg", "c.jpg"), keys)
    }

    /** The default [ImageStore.readerFor]: a snapshot, and nothing beyond it. */
    @Test
    fun theDefaultReaderAnswersFromWhatItWasAskedFor() = runTest {
        val store = FakeImageStore()
        val key = store.write(image)

        val reader = store.readerFor(listOf(key, "missing.jpg"))

        assertEquals(image.toList(), reader.read(key)?.toList())
        assertNull(reader.read("missing.jpg"))
        assertNull(reader.read(store.write(otherImage)))
    }
}

private fun parsedLog(question: String, imageData: List<String>) =
    CardImportExport.ParsedReviewLog(
        cardQuestion = question,
        reviewTime = 1_700_000_000_000,
        grade = 3,
        algorithm = "FSRS",
        stateBefore = "NEW",
        stateAfter = "REVIEW",
        scheduledDays = 1,
        elapsedDays = 0,
        imageData = imageData
    )

private fun card(question: String, imagePaths: List<String>) =
    Card(question = question, answer = "", imagePaths = imagePaths)

private fun reviewLog(imagePaths: String) =
    ReviewLog(
        cardId = 1,
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

/** An [ImageStore] that keeps its files in a map, keyed the way a real one is. */
private class FakeImageStore : ImageStore {

    val files = mutableMapOf<String, ByteArray>()

    override suspend fun read(key: String): ByteArray? = files[key]

    override suspend fun write(bytes: ByteArray, extension: String): String {
        val key = contentKey(bytes, extension)
        files[key] = bytes
        return key
    }

    override suspend fun delete(key: String) {
        files.remove(key)
    }
}
