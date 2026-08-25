// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.util.ParsedCard

/**
 * Moving decks in and out of the app as files.
 *
 * Every method here ends in a file chooser and a stream, which on Android
 * means a Uri and a ContentResolver and in a browser means something else
 * entirely. The screens never name either: they say what the user asked for
 * -- export these groups, import a CSV into this one -- and the platform
 * decides what that means.
 *
 * Progress is not reported through return values because none of this is
 * synchronous. It goes to the view models' importExportState, which the
 * screens already render.
 *
 * [Unavailable] is what the browser uses today. It is deliberately loud
 * rather than silent: import and export are the only part of the app the web
 * build cannot do yet, and a button that quietly does nothing is worse than
 * one that says why.
 */
interface FileTransfer {

    fun importCards()
    fun exportAllCards(includeHistory: Boolean)
    fun exportGroups(groupIds: List<Long>, includeHistory: Boolean)

    fun importCardsCsv()
    fun exportAllCardsCsv()
    fun exportGroupsCsv(groupIds: List<Long>)

    fun importIntoGroup(group: Group)
    fun exportGroup(group: Group)
    fun importCsvIntoGroup(group: Group)
    fun exportGroupCsv(group: Group)

    /** Finishes a CSV import the user has chosen a destination group for. */
    fun csvImportInto(parsed: List<ParsedCard>, errors: List<String>, groupId: Long)
    fun csvImportIntoNewGroup(parsed: List<ParsedCard>, errors: List<String>, groupName: String)
}

/**
 * Reports that file transfer is not available, through the same state the
 * screens already show errors in.
 */
class UnavailableFileTransfer(
    private val report: (String) -> Unit
) : FileTransfer {

    private val message =
        "Import and export are not available in the browser yet. " +
            "The Android app can read and write these files."

    override fun importCards() = report(message)
    override fun exportAllCards(includeHistory: Boolean) = report(message)
    override fun exportGroups(groupIds: List<Long>, includeHistory: Boolean) = report(message)
    override fun importCardsCsv() = report(message)
    override fun exportAllCardsCsv() = report(message)
    override fun exportGroupsCsv(groupIds: List<Long>) = report(message)
    override fun importIntoGroup(group: Group) = report(message)
    override fun exportGroup(group: Group) = report(message)
    override fun importCsvIntoGroup(group: Group) = report(message)
    override fun exportGroupCsv(group: Group) = report(message)
    override fun csvImportInto(parsed: List<ParsedCard>, errors: List<String>, groupId: Long) =
        report(message)
    override fun csvImportIntoNewGroup(
        parsed: List<ParsedCard>,
        errors: List<String>,
        groupName: String
    ) = report(message)
}
