// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ParsedCard

/**
 * Moving decks in and out of the app as files.
 *
 * Every method here ends in a file chooser, which on Android means an
 * activity result and a Uri and in a browser means a hidden file input or a
 * download link. The screens never name either: they say what the user asked for
 * -- export these groups, import a CSV into this one -- and the platform
 * decides what that means.
 *
 * What each method does with the chosen file is not platform-specific and is
 * not here: it hands the file to the card or group view model as an
 * [ImportFile] or an [ExportFile], and the shared code does the rest.
 *
 * Progress is not reported through return values because none of this is
 * synchronous. It goes to the view models' importExportState, which the
 * screens already render.
 */
interface FileTransfer {

    fun importCards()
    fun exportAllCards(includeHistory: Boolean)
    fun exportGroups(groupIds: List<Long>, includeHistory: Boolean)

    fun importCardsCsv()
    fun exportAllCardsCsv()
    fun exportGroupsCsv(groupIds: List<Long>)

    /**
     * Saves every card and review photo as one archive.
     *
     * Photos already leave inside an export, inlined as base64 -- but only
     * in a file that nothing except this app reads. This is the same pictures
     * in a form a photo viewer opens, which is what someone asking to get
     * their photos out means.
     */
    fun exportAllPhotos()

    fun importIntoGroup(group: Group)
    fun exportGroup(group: Group)
    fun importCsvIntoGroup(group: Group)
    fun exportGroupCsv(group: Group)

    /** Finishes a CSV import the user has chosen a destination group for. */
    fun csvImportInto(parsed: List<ParsedCard>, errors: List<String>, groupId: Long)
    fun csvImportIntoNewGroup(parsed: List<ParsedCard>, errors: List<String>, groupName: String)
}

/**
 * A file the user chose to read.
 *
 * Text rather than a stream, because the two platforms have no stream in
 * common and the import format is read whole anyway -- a YAML document is
 * parsed as one thing, and so was the tab-separated format it replaced.
 *
 * Decompression happens before this returns, on the side that knows how: an
 * export written by this app is gzipped, one edited by hand is usually not,
 * and both import. Which it is has to be read out of the file's first two
 * bytes: the archive picker accepts every MIME type there is, so neither the
 * name nor the type the chooser reports is a guide.
 */
interface ImportFile {

    /** The name it was chosen under, which a CSV import derives a group name from. */
    val name: String

    /** Its text, or null if the file could not be read. */
    suspend fun text(): String?
}

/**
 * A file the user asked the app to write.
 *
 * [write] is given the formatting to run rather than the finished text, so
 * that a platform which can stream does: on Android the [Appendable] is a
 * writer over the chosen document and an export of a large collection never
 * exists in memory at once. A browser builds the string and then downloads
 * it, because that is the only way a browser hands a file to anyone.
 *
 * The formatting reports its own [ExportResult] -- how many rows it wrote, or
 * why it stopped -- and [write] returns that, or its own error if the file
 * could not be opened at all.
 */
interface ExportFile {
    suspend fun write(content: (Appendable) -> ExportResult): ExportResult
}

/**
 * A file of bytes the user asked the app to write.
 *
 * [ExportFile]'s counterpart for the things that are not text. It takes the
 * finished bytes rather than something that produces them, because neither
 * thing it writes can be streamed anyway: a photo is already in memory once
 * it is read out of the store, and a zip's central directory is only known
 * after every entry has been written, so the archive is assembled whole
 * either way.
 *
 * Returns null when the file was written, or the reason it was not -- rather
 * than an [ExportResult], because how many photos went in is the caller's
 * count and not something writing a file can report.
 */
interface BinaryExportFile {
    suspend fun write(bytes: ByteArray): String?
}
