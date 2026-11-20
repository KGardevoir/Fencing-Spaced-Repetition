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
 * @param triggerConceptUid the concept a card drawn by concept was drawn for.
 *   Drawn because it was due ([SelectionReason.DUE]): the due concept whose
 *   need made up the largest part of its score, one of [conceptUids]. Extra
 *   study ([SelectionReason.EXTRA]): its concept coming due soonest, which
 *   puts it first in line for the top-up. Kept by a locked draw
 *   ([SelectionReason.LOCKED]): the one from the draw the lock kept. Null for
 *   a card with no concepts, and one not drawn by concept.
 */
data class CardSelection(
    val mode: SelectionMode,
    val reason: SelectionReason,
    val conceptUids: List<String> = emptyList(),
    val triggerConceptUid: String? = null
)

/** A card drawn for a session, and why. */
data class DrawnCard(val card: Card, val selection: CardSelection)
