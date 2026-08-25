// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import com.fencing.spacedrepetition.util.ParsedCard

/**
 * Where an import or export has got to.
 *
 * Moved out of GroupViewModel so the screen that renders it can live in
 * shared code while the view model that produces it stays on Android. Same
 * package as before, so nothing that already referred to it had to change.
 */
sealed class ImportExportState {
    object Idle : ImportExportState()
    object Loading : ImportExportState()
    data class ImportSuccess(val importedCount: Int, val skippedCount: Int, val errors: List<String>) : ImportExportState()
    data class ExportSuccess(val exportedCount: Int) : ImportExportState()
    data class Error(val message: String) : ImportExportState()
    /** CSV import parsed cards and is waiting for the user to select/create a group */
    data class CsvPendingGroupSelection(
        val parsedCards: List<ParsedCard>,
        val parseErrors: List<String>,
        val suggestedGroupName: String
    ) : ImportExportState()
}

enum class GroupSortOption(val label: String) {
    NAME("Name"),
    CARD_COUNT("Card Count")
}
