// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// Where images cross the import/export boundary.
//
// The format code in CardImportExport deals in base64 strings and knows
// nothing about storage; the store deals in bytes and knows nothing about the
// format. These four functions are the join, and they suspend, because
// storing an image in a browser does.
//
// Extensions on CardImportExport rather than members so the object stays
// what its own header claims: parsing and formatting, no storage.

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.ReviewLog

/**
 * Stores the inline base64 images from an import and returns their keys.
 *
 * Content-addressed keys, not file paths, which is the whole point of doing
 * this through the store: importing the same deck twice, or importing a
 * friend's export that shares photographs with your own, stores each picture
 * once. Images whose base64 will not decode are dropped rather than failing
 * the import -- a card with a corrupt picture is still a card.
 */
suspend fun CardImportExport.storeImages(imageData: List<String>, images: ImageStore): List<String> =
    imageData.mapNotNull { data ->
        decodeBase64Image(data)?.let { bytes -> images.write(bytes) }
    }

/**
 * Converts a parsed record to a Card, storing its images on the way.
 *
 * The synchronous [CardImportExport.parsedCardToCard] cannot: it has no store
 * to write to, so it carries whatever paths the record already held. This is
 * the one an import wants.
 */
suspend fun CardImportExport.parsedCardToCard(parsed: ParsedCard, images: ImageStore): Card =
    parsedCardToCard(parsed).copy(imagePaths = storeImages(parsed.imageData, images))

/**
 * Converts parsed review logs to entities, storing the images they carry.
 *
 * The conversion itself is [CardImportExport.parsedReviewLogsToEntities],
 * called a log at a time so that a column added to a review log is added in
 * one place rather than two. What is added here is the part that has to wait
 * on storage, and so cannot be in a synchronous function: the images.
 *
 * Logs whose card is not in the map are skipped there, and stay skipped here:
 * an export's history section can outlive the card it refers to, and a log
 * with no card to hang off is not something the history screen could show.
 */
suspend fun CardImportExport.parsedReviewLogsToEntities(
    parsed: List<CardImportExport.ParsedReviewLog>,
    questionToCardId: Map<String, Long>,
    opponentNameToId: Map<String, Long> = emptyMap(),
    images: ImageStore
): List<ReviewLog> = parsed.mapNotNull { log ->
    val entity = parsedReviewLogsToEntities(listOf(log), questionToCardId, opponentNameToId)
        .singleOrNull() ?: return@mapNotNull null
    entity.copy(imagePaths = storeImages(log.imageData, images).joinToString(","))
}

/**
 * Every image key an export of these rows will ask for.
 *
 * [ImageStore.readerFor] needs them up front, because a store that has to be
 * awaited cannot be reached from inside the formatting. A card holds its keys
 * as a list; a review log holds them as one comma-separated column, which is
 * the shape the database has always stored them in.
 */
fun exportImageKeys(cards: List<Card>, reviewLogs: List<ReviewLog> = emptyList()): Set<String> {
    val keys = mutableSetOf<String>()
    cards.forEach { keys.addAll(it.imagePaths) }
    reviewLogs.forEach { log ->
        log.imagePaths.split(",").forEach { key -> if (key.isNotBlank()) keys.add(key) }
    }
    return keys
}

/**
 * The photos of these rows, named for a zip a person will open.
 *
 * Stored keys are content hashes, so an archive of them under their own names
 * would be a folder of sixty-four hex digits -- technically the export the
 * user asked for and of no use to anyone. Each photo is named for the card it
 * belongs to instead, under `cards/` or `reviews/` depending on whether it was
 * attached to the card or to a note taken while practising it.
 *
 * A photo attached in both places, or to two cards -- which the store allows,
 * because identical bytes are one file -- goes in once, under the first name
 * found for it. Emitting it twice would inflate both the archive and the count
 * the user is shown, and neither copy would be more correct than the other.
 *
 * [images] is a reader rather than the store because a browser's store has to
 * be awaited and this is not a suspending function; the caller loads the keys
 * up front, as an archive export does. A photo that will not read is left out
 * rather than failing the export -- the same rule the deck export follows.
 */
fun photoArchiveEntries(
    cards: List<Card>,
    reviewLogs: List<ReviewLog>,
    images: ImageReader
): List<ZipEntry> {
    val entries = mutableListOf<ZipEntry>()
    val taken = mutableSetOf<String>()
    val seenKeys = mutableSetOf<String>()
    val questions = cards.associate { it.id to it.question }

    fun add(key: String, folder: String, cardName: String) {
        if (!seenKeys.add(key)) return
        val bytes = images.read(key) ?: return
        entries.add(ZipEntry(uniqueName(folder, cardName, key, taken), bytes))
    }

    cards.forEach { card ->
        card.imagePaths.forEach { key -> add(key, "cards", card.question) }
    }
    reviewLogs.forEach { log ->
        val name = questions[log.cardId] ?: "review"
        log.imagePaths.split(",").forEach { key ->
            if (key.isNotBlank()) add(key, "reviews", name)
        }
    }
    return entries
}

/**
 * `cards/Parry_four.jpg`, and `cards/Parry_four_2.jpg` for the next one.
 *
 * Cards routinely carry several photos and two cards may be named the same,
 * so a name has to be claimed rather than assumed. The extension comes off the
 * key, which is where the store recorded what kind of image it holds.
 */
private fun uniqueName(
    folder: String,
    cardName: String,
    key: String,
    taken: MutableSet<String>
): String {
    val extension = key.substringAfterLast('.', "jpg")
    val base = CardImportExport.sanitizeForFilename(cardName).ifBlank { "card" }
    var name = "$folder/$base.$extension"
    var next = 2
    while (!taken.add(name)) {
        name = "$folder/${base}_$next.$extension"
        next++
    }
    return name
}
