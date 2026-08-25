// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// The Android half of the import/export format: streams and gzip. Everything
// that does not need them is in :shared, in commonMain, and compiles for the
// browser too -- including the conversions that store an imported image,
// which used to be here because they needed a Context and now go through the
// image store instead.
//
// These are extensions on CardImportExport rather than members so the object
// itself can be common. Call sites are unchanged apart from an import.

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

// ==========================================================================
// Stream adapters.
//
// Everything in the format code operates on a List<String>, a String or an
// Appendable, and so has no reason to be tied to a JVM. These give it back
// the InputStream/OutputStream surface the backup worker and the tests use,
// so the split costs nothing at those call sites. They are the part that
// stayed behind when the core moved to common code -- a browser has no
// java.io.
// ==========================================================================

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
    images: ImageReader
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
    images: ImageReader
): ExportResult = outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
    exportCardsToCsv(cardsWithGroups, writer, images)
}
