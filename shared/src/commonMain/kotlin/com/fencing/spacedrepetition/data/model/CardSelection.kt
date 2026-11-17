// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/** How a session's cards were drawn: by the cards' own states, or by learning concept. */
enum class SelectionMode { CARD, CONCEPT }

/** Why a card is in a session (or graded at all). Recorded on its review log for analysis. */
enum class SelectionReason {
    /** It was due: the card itself, or -- drawn by concept -- one of [CardSelection.conceptUids]. */
    DUE,
    /** Additional study: a session the due cards did not fill was topped up with it. */
    EXTRA,
    /** The group's draw was locked, so the session kept the cards drawn before. */
    LOCKED,
    /** Graded from the card editor, not drawn at all. */
    EDITOR
}

/**
 * Why a card was chosen for practice.
 *
 * @param conceptUids for a card drawn by concept to cover due concepts, the
 *   stable identifiers of the due concepts it was picked to cover.
 */
data class CardSelection(
    val mode: SelectionMode,
    val reason: SelectionReason,
    val conceptUids: List<String> = emptyList()
)

/** A card drawn for a session, and why. */
data class DrawnCard(val card: Card, val selection: CardSelection)
