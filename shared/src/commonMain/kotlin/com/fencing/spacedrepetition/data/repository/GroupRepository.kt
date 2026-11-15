// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.util.CardWithGroupStates
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.GroupWithCards
import com.fencing.spacedrepetition.data.model.LearningGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class GroupRepository(
    private val groupDao: GroupDao,
    private val cardDao: CardDao
) {
    // Group CRUD
    fun getAllGroups(): Flow<List<Group>> = groupDao.getAllGroups()

    suspend fun getGroupById(groupId: Long): Group? = groupDao.getGroupById(groupId)

    fun getGroupByIdFlow(groupId: Long): Flow<Group?> = groupDao.getGroupByIdFlow(groupId)

    suspend fun insertGroup(group: Group): Long = groupDao.insertGroup(group)

    suspend fun updateGroup(group: Group) = groupDao.updateGroup(group)

    suspend fun deleteGroup(group: Group) = groupDao.deleteGroup(group)

    fun getGroupCount(): Flow<Int> = groupDao.getGroupCount()

    // Card-Group relations
    fun getGroupsForCard(cardId: Long): Flow<List<Group>> = groupDao.getGroupsForCard(cardId)

    suspend fun getGroupsForCardSync(cardId: Long): List<Group> = groupDao.getGroupsForCardSync(cardId)

    suspend fun setGroupsForCard(cardId: Long, groupIds: List<Long>) {
        // Remove existing associations
        groupDao.deleteAllGroupsForCard(cardId)
        // Add new associations
        groupIds.forEach { groupId ->
            groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
        }
    }

    suspend fun addCardToGroup(cardId: Long, groupId: Long) {
        groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
    }

    suspend fun removeCardFromGroup(cardId: Long, groupId: Long) {
        groupDao.deleteCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
    }

    // Cards by group
    fun getCardsByGroup(groupId: Long): Flow<List<Card>> = cardDao.getCardsByGroup(groupId)

    suspend fun getCardsByGroupSync(groupId: Long): List<Card> = cardDao.getCardsByGroupSync(groupId)

    suspend fun getDueCardsByGroup(groupId: Long, limit: Int = 100): List<Card> =
        cardDao.getDueCardsByGroup(groupId, limit = limit)

    fun getDueCardCountByGroup(groupId: Long): Flow<Int> = cardDao.getDueCardCountByGroup(groupId)

    fun getGroupWithCards(groupId: Long): Flow<GroupWithCards?> = groupDao.getGroupWithCards(groupId)

    fun getAllGroupsWithCards(): Flow<List<GroupWithCards>> = groupDao.getAllGroupsWithCards()

    suspend fun getAllGroupsSync(): List<Group> = getAllGroups().first()

    suspend fun getGroupNameMap(): Map<String, Long> {
        return getAllGroupsSync().associate { it.name to it.id }
    }

    suspend fun getCardsByGroupWithGroupNames(groupId: Long): List<Pair<Card, List<String>>> {
        val cards = getCardsByGroupSync(groupId)
        return cards.map { card ->
            val groups = getGroupsForCardSync(card.id)
            card to groups.map { it.name }
        }
    }

    suspend fun getCardsByGroupWithStates(groupId: Long): List<CardWithGroupStates> {
        val learningGroupNames = getAllLearningGroupsSync().associate { it.id to it.name }
        return getCardsByGroupSync(groupId).map { card ->
            val groups = getGroupsForCardSync(card.id)
            CardWithGroupStates(
                card,
                groups.map { it.name },
                learningStatesByName(card.id, groups, learningGroupNames)
            )
        }
    }

    /**
     * The learning states [cardId] holds in the learning groups its [groups]
     * practise against, by learning group name -- what an export writes.
     */
    internal suspend fun learningStatesByName(
        cardId: Long,
        groups: List<Group>,
        learningGroupNames: Map<Long, String>
    ): Map<String, CardLearningState> {
        val learningGroupIds = groups.mapNotNull { it.learningGroupId }.toSet()
        if (learningGroupIds.isEmpty()) return emptyMap()
        return groupDao.getAllLearningStatesForCard(cardId)
            .filter { it.learningGroupId in learningGroupIds }
            .mapNotNull { state -> learningGroupNames[state.learningGroupId]?.let { it to state } }
            .toMap()
    }

    /**
     * Ensures all group names exist, creating any that don't.
     * Returns an updated map of group name to ID.
     *
     * [legacyIndependentGroups] names groups that an older export recorded as
     * learning independently. Each is put in a learning group of its own name
     * -- created if need be -- unless it already belongs to one, which is how
     * a file from before learning groups keeps its meaning.
     */
    suspend fun ensureGroupsExist(
        groupNames: Set<String>,
        legacyIndependentGroups: Set<String> = emptySet()
    ): Map<String, Long> {
        val existingGroups = getAllGroupsSync().associate { it.name to it.id }.toMutableMap()

        groupNames.forEach { name ->
            if (name.isNotBlank() && !existingGroups.containsKey(name)) {
                existingGroups[name] = groupDao.insertGroup(Group(name = name, description = "Imported group"))
            }
        }
        legacyIndependentGroups.forEach { name ->
            val group = existingGroups[name]?.let { groupDao.getGroupById(it) } ?: return@forEach
            if (group.learningGroupId == null) {
                setLearningGroup(group.id, ensureLearningGroup(name))
            }
        }

        return existingGroups
    }

    suspend fun getGroupByName(name: String): Group? = groupDao.getGroupByName(name)

    fun getAllLearningStatesForCard(cardId: Long): Flow<List<CardLearningState>> =
        groupDao.getAllLearningStatesForCardFlow(cardId)

    suspend fun getAllLearningStatesForCardSync(cardId: Long): List<CardLearningState> =
        groupDao.getAllLearningStatesForCard(cardId)

    // Learning groups
    fun getAllLearningGroups(): Flow<List<LearningGroup>> = groupDao.getAllLearningGroups()

    suspend fun getAllLearningGroupsSync(): List<LearningGroup> = groupDao.getAllLearningGroups().first()

    suspend fun getLearningGroupById(id: Long): LearningGroup? = groupDao.getLearningGroupById(id)

    suspend fun insertLearningGroup(learningGroup: LearningGroup): Long =
        groupDao.insertLearningGroup(learningGroup)

    suspend fun updateLearningGroup(learningGroup: LearningGroup) =
        groupDao.updateLearningGroup(learningGroup)

    /**
     * Deletes a learning group and every state kept in it. Its card groups go
     * back to Global, by the foreign key, rather than being deleted with it.
     */
    /** How many different cards [groupIds] hold between them. */
    suspend fun countCardsInGroups(groupIds: Set<Long>): Int =
        groupDao.countDistinctCardsInGroups(groupIds.toList())

    suspend fun deleteLearningGroup(learningGroup: LearningGroup) =
        groupDao.deleteLearningGroup(learningGroup)

    /** The id of the learning group called [name], creating it if there is none. */
    suspend fun ensureLearningGroup(name: String, description: String = ""): Long =
        groupDao.getLearningGroupByName(name)?.id
            ?: groupDao.insertLearningGroup(LearningGroup(name = name, description = description))

    /**
     * Points a card group at a learning group, or at Global for null.
     *
     * A card that has no state in the learning group yet starts from its
     * Global one, so moving a group in does not make every card in it new
     * again. A card that already has one -- because another group in the
     * same learning group holds it -- keeps it: that is the progress being
     * shared.
     */
    suspend fun setLearningGroup(groupId: Long, learningGroupId: Long?) {
        groupDao.setLearningGroupForGroup(groupId, learningGroupId)
        if (learningGroupId == null) return
        cardDao.getCardsByGroupSync(groupId).forEach { card ->
            groupDao.insertLearningStateIfAbsent(
                CardLearningState(
                    cardId = card.id,
                    learningGroupId = learningGroupId,
                    fsrsStability = card.fsrsStability,
                    fsrsDifficulty = card.fsrsDifficulty,
                    fsrsElapsedDays = card.fsrsElapsedDays,
                    fsrsScheduledDays = card.fsrsScheduledDays,
                    fsrsReps = card.fsrsReps,
                    fsrsLapses = card.fsrsLapses,
                    fsrsState = card.fsrsState,
                    lastReview = card.lastReview,
                    nextReview = card.nextReview
                )
            )
        }
    }
}
