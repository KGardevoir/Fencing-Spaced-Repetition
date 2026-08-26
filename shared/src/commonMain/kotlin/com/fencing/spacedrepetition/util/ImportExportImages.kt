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
