// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * Which concepts the card editor offers for a card.
 *
 * A card is learnt in the learning contexts of its groups -- each group's
 * learning group, or Global for a group in none and for a card in no group at
 * all. A concept is offered when it is available in one of those contexts: a
 * learning group it is linked to, or Global when [Concept.inGlobal]. A
 * concept with no availability set at all -- neither -- is available
 * everywhere: setting availability restricts a concept, and every concept
 * made before availability existed, or imported from a file from before it,
 * stays usable as it was.
 *
 * Whether a context schedules by concept does not matter here: tagging is
 * always allowed, and a context with concept scheduling off simply does not
 * use the tags to draw sessions (see [LearningContexts.schedulingByConcept],
 * which the editor uses to say so). Only offering is restricted; a card already tagged with a
 * concept that is not offered keeps the tag.
 *
 * @param cardContexts the card's learning contexts: learning group ids, null for Global.
 */
fun offeredConceptIds(
    concepts: List<Concept>,
    links: List<ConceptLearningGroupCrossRef>,
    cardContexts: Set<Long?>
): Set<Long> {
    val learningGroupsByConcept = links.groupBy({ it.conceptId }, { it.learningGroupId })
    return concepts.filter { concept ->
        val learningGroups = learningGroupsByConcept[concept.id].orEmpty()
        val unrestricted = !concept.inGlobal && learningGroups.isEmpty()
        unrestricted ||
            (null in cardContexts && concept.inGlobal) ||
            learningGroups.any { it in cardContexts }
    }.map { it.id }.toSet()
}
