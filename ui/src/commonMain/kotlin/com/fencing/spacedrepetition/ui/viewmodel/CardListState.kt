// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

/**
 * How the card list is ordered.
 *
 * Moved out of CardViewModel so the screen that offers these choices can live
 * in shared code while the view model that applies them stays on Android.
 * Same package as before, so nothing that already referred to them changed.
 */
enum class CardSortOption(val label: String) {
    DUE_DATE("Due Date"),
    NAME("Name"),
    REVIEWS("Reviews"),
    DIFFICULTY("Difficulty")
}

enum class SortDirection {
    ASCENDING,
    DESCENDING
}
