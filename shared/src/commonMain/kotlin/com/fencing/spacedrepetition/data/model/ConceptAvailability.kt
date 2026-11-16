// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * Which concepts the card editor offers for a card.
 *
 * A card is learnt in the learning contexts of its groups -- each group's
 * learning group, or Global for a group in none and for a card in no group at
 * all. A concept is offered when it belongs to one of those contexts (a
 * learning group it is linked to, or Global when [Concept.inGlobal]) and that
 * context schedules by concept.
 *
 * Only offering is restricted. A card already tagged with a concept that is
 * not offered keeps the tag; the editor shows it so it can be removed.
 *
 * @param cardContexts the card's learning contexts: learning group ids, null for Global.
 * @param globalSchedulesByConcept Global's concept-scheduling switch.
 * @param learningGroups every learning group, for its switch.
 */
fun offeredConceptIds(
    concepts: List<Concept>,
    links: List<ConceptLearningGroupCrossRef>,
    cardContexts: Set<Long?>,
    globalSchedulesByConcept: Boolean,
    learningGroups: List<LearningGroup>
): Set<Long> {
    val usable = conceptContexts(cardContexts, globalSchedulesByConcept, learningGroups)
    if (usable.isEmpty()) return emptySet()
    val learningGroupsByConcept = links.groupBy({ it.conceptId }, { it.learningGroupId })
    return concepts.filter { concept ->
        (null in usable && concept.inGlobal) ||
            learningGroupsByConcept[concept.id].orEmpty().any { it in usable }
    }.map { it.id }.toSet()
}

/** Of [cardContexts], the ones that schedule by concept: where a concept can be offered at all. */
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
