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

/**
 * What the card editor offers a card, for the groups it has selected: the
 * learning contexts it is learnt in, the concepts available there
 * ([offeredConceptIds]), and a note when some of those contexts do not
 * schedule by concept -- tagging is allowed either way.
 */
class ConceptOffer(
    private val contexts: LearningContexts,
    private val concepts: List<Concept>,
    private val links: List<ConceptLearningGroupCrossRef>
) {
    fun contextsFor(groupIds: Set<Long>): Set<Long?> = contexts.ofCardInGroups(groupIds)

    fun offeredFor(groupIds: Set<Long>): Set<Long> = offeredConceptIds(concepts, links, contextsFor(groupIds))

    /** Where concept scheduling is off for the card, said for the editor; null when it is on everywhere. */
    fun schedulingOffNote(groupIds: Set<Long>, globalByConcept: Boolean): String? {
        val cardContexts = contextsFor(groupIds)
        val on = contexts.schedulingByConcept(cardContexts, globalByConcept)
        val off = (cardContexts - on).map(contexts::name).sorted().joinToString(", ")
        return when {
            off.isEmpty() -> null
            on.isEmpty() -> "Concept scheduling is off for $off. Tags are kept, but this card is " +
                "scheduled card by card until it is turned on."
            else -> "Concept scheduling is off in $off: there, this card is scheduled card by card."
        }
    }

    companion object {
        val NONE = ConceptOffer(LearningContexts(emptyList(), emptyList()), emptyList(), emptyList())
    }
}
