package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.GroupWithCards
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

    suspend fun getCardsByGroupWithStates(groupId: Long): List<com.fencing.spacedrepetition.util.CardWithGroupStates> {
        val cards = getCardsByGroupSync(groupId)
        val allGroups = getAllGroupsSync()
        val independentLearningGroupNames = allGroups.filter { it.independentLearning }.map { it.name to it.id }.toMap()

        return cards.map { card ->
            val groups = getGroupsForCardSync(card.id)
            val groupNames = groups.map { it.name }

            // Get group-specific states for groups with independent learning
            val groupSpecificStates = mutableMapOf<String, com.fencing.spacedrepetition.data.model.CardGroupLearningState>()
            groups.forEach { group ->
                if (group.independentLearning) {
                    groupDao.getLearningState(card.id, group.id)?.let { state ->
                        groupSpecificStates[group.name] = state
                    }
                }
            }

            com.fencing.spacedrepetition.util.CardWithGroupStates(card, groupNames, groupSpecificStates)
        }
    }

    /**
     * Ensures all group names exist, creating any that don't.
     * Returns an updated map of group name to ID.
     */
    suspend fun ensureGroupsExist(groupNames: Set<String>): Map<String, Long> {
        val existingGroups = getAllGroupsSync().associate { it.name to it.id }.toMutableMap()

        groupNames.forEach { name ->
            if (name.isNotBlank() && !existingGroups.containsKey(name)) {
                val newGroup = Group(name = name, description = "Imported group")
                val newId = groupDao.insertGroup(newGroup)
                existingGroups[name] = newId
            }
        }

        return existingGroups
    }

    suspend fun getGroupByName(name: String): Group? = groupDao.getGroupByName(name)

    suspend fun toggleIndependentLearning(groupId: Long, enabled: Boolean) {
        val group = groupDao.getGroupById(groupId) ?: return
        val updatedGroup = group.copy(independentLearning = enabled)
        groupDao.updateGroup(updatedGroup)

        // If enabling independent learning, initialize learning states for all cards in the group
        if (enabled) {
            val cards = cardDao.getCardsByGroupSync(groupId)
            cards.forEach { card ->
                val existingState = groupDao.getLearningState(card.id, groupId)
                if (existingState == null) {
                    groupDao.insertLearningState(
                        com.fencing.spacedrepetition.data.model.CardGroupLearningState(
                            cardId = card.id,
                            groupId = groupId,
                            // Copy current card state to the new learning state
                            fsrsStability = card.fsrsStability,
                            fsrsDifficulty = card.fsrsDifficulty,
                            fsrsElapsedDays = card.fsrsElapsedDays,
                            fsrsScheduledDays = card.fsrsScheduledDays,
                            fsrsReps = card.fsrsReps,
                            fsrsLapses = card.fsrsLapses,
                            fsrsState = card.fsrsState,
                            sm2EaseFactor = card.sm2EaseFactor,
                            sm2Interval = card.sm2Interval,
                            sm2Repetitions = card.sm2Repetitions,
                            lastReview = card.lastReview,
                            nextReview = card.nextReview
                        )
                    )
                }
            }
        }
    }
}
