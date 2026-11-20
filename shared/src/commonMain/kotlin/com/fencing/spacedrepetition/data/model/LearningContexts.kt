// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * A learning context: Global ([learningGroupId] null), or one learning group.
 * Progress is kept per context; see docs/DESIGN.md, "Learning contexts".
 */
data class LearningContext(val learningGroupId: Long?) {
    companion object {
        val GLOBAL = LearningContext(null)
    }
}

/**
 * Which learning context things are learnt in, worked out from the card
 * groups and learning groups as they are now. The one place for the rules in
 * docs/DESIGN.md, "Learning contexts":
 *
 * - a card group learns in its learning group, or Global when it is in none;
 * - a card learns in each of its groups' contexts, or Global when it is in no
 *   group;
 * - a review belongs to the context of the group it was made in, as that
 *   group is now -- found by its stable identifier, or, for a review recorded
 *   without one, by the name it noted (the oldest group of that name). Made
 *   in no group (all-cards practice, the card editor) is Global; made in a
 *   group that no longer exists is no context at all.
 *
 * Contexts are given as learning group ids, null for Global.
 */
class LearningContexts(groups: List<Group>, val learningGroups: List<LearningGroup>) {
    private val learningGroupsById = learningGroups.associateBy { it.id }
    private val groupsById = groups.associateBy { it.id }
    private val groupsByUid = groups.associateBy { it.uid }
    private val groupsByName = groups.sortedBy { it.id }.groupBy { it.name }.mapValues { it.value.first() }

    /** A group's context: its learning group, or Global for a group in none. */
    fun of(group: Group): Long? = group.learningGroupId?.takeIf { it in learningGroupsById }

    /** The context of the group with [groupId]; Global for no group, or one that is gone. */
    fun ofGroupId(groupId: Long?): Long? = groupId?.let { groupsById[it] }?.let(::of)

    /** A card's contexts, from the groups it is in: Global for a card in none. */
    fun ofCard(cardGroups: Collection<Group>): Set<Long?> =
        if (cardGroups.isEmpty()) setOf(null) else cardGroups.map(::of).toSet()

    /** [ofCard], for groups given by id (as the card editor holds them). */
    fun ofCardInGroups(groupIds: Collection<Long>): Set<Long?> = ofCard(groupIds.mapNotNull { groupsById[it] })

    /** The group a review was made in, as it is now; see the class notes. */
    fun groupOf(uid: String?, name: String?): Group? =
        if (uid != null) groupsByUid[uid] else name?.let { groupsByName[it] }

    fun groupOf(log: ReviewLog): Group? = groupOf(log.groupUid, log.groupName)

    /**
     * The context a review belongs to, or null when it cannot be placed: its
     * group no longer exists.
     */
    fun ofReview(log: ReviewLog): LearningContext? {
        val madeInNoGroup = log.groupUid == null && (log.groupName.isNullOrBlank() || log.groupName == CARD_EDITOR)
        if (madeInNoGroup) return LearningContext.GLOBAL
        return groupOf(log)?.let { LearningContext(of(it)) }
    }

    /** The learning group of [context]; null for Global. */
    fun learningGroup(context: Long?): LearningGroup? = context?.let { learningGroupsById[it] }

    /** Whether [context] schedules by concept: Global's setting, or the learning group's switch. */
    fun schedulesByConcept(context: Long?, globalByConcept: Boolean): Boolean =
        if (context == null) globalByConcept else learningGroupsById[context]?.useConcepts == true

    /** Of [contexts], the ones that schedule by concept -- where tags affect what is drawn. */
    fun schedulingByConcept(contexts: Set<Long?>, globalByConcept: Boolean): Set<Long?> =
        contexts.filter { schedulesByConcept(it, globalByConcept) }.toSet()

    /** A context's name for notes: the learning group's, or "Global". */
    fun name(context: Long?): String =
        if (context == null) "Global" else learningGroupsById[context]?.name ?: "a learning group"

    companion object {
        /** The group name a grade from the card editor records. */
        const val CARD_EDITOR = "card_edit"
    }
}
