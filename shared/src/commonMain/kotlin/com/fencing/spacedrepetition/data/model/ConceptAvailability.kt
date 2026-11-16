// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * Which concepts the card editor offers for a card.
 *
 * A card is learnt in the learning contexts of its groups -- each group's
 * learning group, or Global for a group in none and for a card in no group at
 * all. A concept is offered when it is available in one of those contexts: a
 * learning group it is linked to, or Global when [Concept.inGlobal].
 *
 * Whether a context schedules by concept does not matter here: tagging is
 * always allowed, and a context with concept scheduling off simply does not
 * use the tags to draw sessions (see [conceptContexts], which the editor uses
 * to say so). Only offering is restricted; a card already tagged with a
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
        (null in cardContexts && concept.inGlobal) ||
            learningGroupsByConcept[concept.id].orEmpty().any { it in cardContexts }
    }.map { it.id }.toSet()
}

/** Of [cardContexts], the ones that schedule by concept -- where tags affect what is drawn. */
fun conceptContexts(
    cardContexts: Set<Long?>,
    globalSchedulesByConcept: Boolean,
    learningGroups: List<LearningGroup>
): Set<Long?> {
    val switchedOn = learningGroups.filter { it.useConcepts }.map { it.id }.toSet()
    return cardContexts.filter { if (it == null) globalSchedulesByConcept else it in switchedOn }.toSet()
}

/** The learning contexts a card in [groupIds] is learnt in: learning group ids, null for Global. */
fun learningContextsOf(groupIds: Collection<Long>, groups: List<Group>): Set<Long?> =
    if (groupIds.isEmpty()) setOf(null)
    else groups.filter { it.id in groupIds }.map { it.learningGroupId }.toSet()

/** A context's name for notes: the learning group's, or "Global". */
fun contextName(context: Long?, learningGroups: List<LearningGroup>): String =
    if (context == null) "Global" else learningGroups.find { it.id == context }?.name ?: "a learning group"
