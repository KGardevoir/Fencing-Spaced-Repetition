// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// The Android half of the import/export format. Everything here needs a
// Context, a file, a stream or gzip; everything that does not is in :shared,
// in commonMain, and compiles for the browser too.
//
// These are extensions on CardImportExport rather than members so the object
// itself can be common. Call sites are unchanged apart from an import.

import android.content.Context
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Converts a ParsedCard to a Card entity with base64 image decoding
 */
fun CardImportExport.parsedCardToCard(context: Context, parsed: ParsedCard): Card {
    val now = Time.now()

    // Decode base64 images to file paths
    val decodedImagePaths = parsed.imageData.mapNotNull { base64Data ->
        decodeImageFromBase64(context, base64Data)
    }

    return if (parsed.hasFullState) {
        Card(
            question = parsed.concept,
            answer = parsed.answer,
            imagePaths = decodedImagePaths,
            algorithm = parsed.algorithm ?: AlgorithmType.FSRS,
            nextReview = parsed.nextReview ?: 0L,
            lastReview = parsed.lastReview ?: 0L,
            fsrsStability = parsed.fsrsStability ?: 0.0,
            fsrsDifficulty = parsed.fsrsDifficulty ?: 0.0,
            fsrsState = parsed.fsrsState ?: "NEW",
            fsrsReps = parsed.fsrsReps ?: 0,
            fsrsLapses = parsed.fsrsLapses ?: 0,
            fsrsScheduledDays = parsed.fsrsScheduledDays ?: 0,
            fsrsElapsedDays = parsed.fsrsElapsedDays ?: 0,
            sm2EaseFactor = parsed.sm2EaseFactor ?: 2.5,
            sm2Interval = parsed.sm2Interval ?: 0,
            sm2Repetitions = parsed.sm2Repetitions ?: 0,
            created = now,
            modified = now
        )
    } else {
        Card(
            question = parsed.concept,
            answer = parsed.answer,
            imagePaths = decodedImagePaths,
            algorithm = AlgorithmType.FSRS,
            created = now,
            modified = now
        )
    }
}

/**
 * Converts ParsedReviewLogs to ReviewLog entities using a question->cardId map.
 * Skips logs for cards not found in the map.
 * Decodes base64 images and saves them to internal storage.
 */
fun CardImportExport.parsedReviewLogsToEntities(
    context: Context,
    parsed: List<ParsedReviewLog>,
    questionToCardId: Map<String, Long>,
    opponentNameToId: Map<String, Long> = emptyMap()
): List<ReviewLog> {
    return parsed.mapNotNull { p ->
        val cardId = questionToCardId[p.cardQuestion] ?: return@mapNotNull null
        // Decode base64 images for review log notes
        val decodedImagePaths = p.imageData.mapNotNull { base64 ->
            decodeImageFromBase64(context, base64, "review_images")
        }
        ReviewLog(
            cardId = cardId,
            sessionId = null,
            reviewTime = p.reviewTime,
            grade = p.grade,
            algorithm = p.algorithm,
            stateBefore = p.stateBefore,
            stateAfter = p.stateAfter,
            scheduledDays = p.scheduledDays,
            elapsedDays = p.elapsedDays,
            groupName = p.groupName,
            notes = p.notes,
            imagePaths = decodedImagePaths.joinToString(","),
            opponentId = p.opponentName?.let { opponentNameToId[it] },
            stabilityMultiplier = p.stabilityMultiplier
        )
    }
}

/**
 * Wraps an OutputStream with GZIP compression
 */
fun CardImportExport.createCompressedOutputStream(outputStream: OutputStream): GZIPOutputStream {
    return GZIPOutputStream(outputStream)
}

/**
 * Wraps an InputStream with GZIP decompression
 */
fun CardImportExport.createDecompressedInputStream(inputStream: InputStream): GZIPInputStream {
    return GZIPInputStream(inputStream)
}

/**
 * Auto-detects whether the input stream is GZIP-compressed by checking magic bytes.
 * Returns a GZIPInputStream if compressed, or the original (buffered) stream if plain text.
 */
fun CardImportExport.smartInputStream(inputStream: InputStream): InputStream {
    val buffered = BufferedInputStream(inputStream)
    buffered.mark(2)
    val byte1 = buffered.read()
    val byte2 = buffered.read()
    buffered.reset()
    // GZIP magic number: 0x1f 0x8b
    return if (byte1 == 0x1f && byte2 == 0x8b) {
        GZIPInputStream(buffered)
    } else {
        buffered
    }
}

/**
 * Decodes one inline base64 image and stores it under the app's files
 * directory, returning the path to reach it by, or null if either step fails.
 * The decode itself is common code; only the storing is Android's.
 */
fun CardImportExport.decodeImageFromBase64(
    context: Context,
    base64Data: String,
    subDir: String = "card_images"
): String? {
    val bytes = decodeBase64Image(base64Data) ?: return null
    return try {
        val imagesDir = File(context.filesDir, subDir)
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        val outputFile = File(imagesDir, "${subDir}_${Time.now()}.jpg")
        outputFile.writeBytes(bytes)
        outputFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

// ==========================================================================
// Stream adapters.
//
// Everything above operates on a List<String>, a String or an Appendable, and
// so has no reason to be tied to a JVM. These six give it back the
// InputStream/OutputStream surface every existing caller uses, so the split
// costs nothing at the call sites. They are the part that stays behind when
// the core moves to common code -- a browser has no java.io.
// ==========================================================================

/**
 * Reads images straight off the filesystem, which is what the export code did
 * inline before the reader became a parameter. Absolute paths, no Context: it
 * is the read half only, and export never writes an image.
 */
object FileImageReader : ImageReader {
    override fun read(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }
}

/** Reads the whole stream as UTF-8 lines and parses it. */
fun CardImportExport.parseCards(inputStream: InputStream): Pair<List<ParsedCard>, List<String>> =
    parseCards(inputStream.bufferedReader(Charsets.UTF_8).readLines())

/** Reads the whole stream as UTF-8 text and parses it as CSV. */
fun CardImportExport.parseCsvCards(inputStream: InputStream): Pair<List<ParsedCard>, List<String>> =
    parseCsvCards(inputStream.bufferedReader(Charsets.UTF_8).readText())

fun CardImportExport.exportCardsWithGroups(
    cardsWithGroups: List<CardWithGroupNames>,
    outputStream: OutputStream
): ExportResult = outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
    exportCardsWithGroups(cardsWithGroups, writer)
}

fun CardImportExport.exportCardsWithGroupStates(
    cardsWithStates: List<CardWithGroupStates>,
    outputStream: OutputStream,
    groupSettings: List<Group> = emptyList(),
    reviewLogs: List<ReviewLog> = emptyList(),
    cardQuestions: Map<Long, String> = emptyMap(),
    opponents: List<Opponent> = emptyList(),
    opponentNamesById: Map<Long, String> = emptyMap(),
    images: ImageReader = FileImageReader
): ExportResult = outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
    exportCardsWithGroupStates(
        cardsWithStates, writer, images, groupSettings, reviewLogs,
        cardQuestions, opponents, opponentNamesById
    )
}

fun CardImportExport.exportCards(cards: List<Card>, outputStream: OutputStream): ExportResult =
    outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
        exportCards(cards, writer)
    }

fun CardImportExport.exportCardsToCsv(
    cardsWithGroups: List<CardWithGroupNames>,
    outputStream: OutputStream,
    images: ImageReader = FileImageReader
): ExportResult = outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
    exportCardsToCsv(cardsWithGroups, writer, images)
}
