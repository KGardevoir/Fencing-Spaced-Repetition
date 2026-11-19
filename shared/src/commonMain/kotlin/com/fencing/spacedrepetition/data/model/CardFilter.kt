// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * What the card list is filtered by. A card is shown when it passes every
 * kind of filter that is set:
 *
 * - [groupIds]: it is in any one of these groups;
 * - [conceptIds]: it carries any one of these concepts;
 * - [showDisabled]: it is disabled when true, enabled when false;
 * - [query]: its question or description contains it, ignoring case.
 *
 * An empty set, or a blank query, does not filter.
 */
data class CardFilter(
    val groupIds: Set<Long> = emptySet(),
    val conceptIds: Set<Long> = emptySet(),
    val showDisabled: Boolean = false,
    val query: String = ""
) {
    /** The cards of [cards] that pass, in the order given. */
    fun apply(cards: List<CardWithGroups>, conceptLinks: List<CardConceptCrossRef>): List<Card> {
        val withConcept = if (conceptIds.isEmpty()) emptySet()
            else conceptLinks.filter { it.conceptId in conceptIds }.map { it.cardId }.toSet()
        val needle = query.trim()
        return cards
            .filter { it.card.isDisabled == showDisabled }
            .filter { groupIds.isEmpty() || it.groups.any { group -> group.id in groupIds } }
            .filter { conceptIds.isEmpty() || it.card.id in withConcept }
            .map { it.card }
            .filter {
                needle.isEmpty() ||
                    it.question.contains(needle, ignoreCase = true) ||
                    it.answer.contains(needle, ignoreCase = true)
            }
    }
}
