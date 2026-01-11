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
}
