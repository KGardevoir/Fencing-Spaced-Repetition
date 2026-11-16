// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.GroupWithCards
import com.fencing.spacedrepetition.data.model.LearningGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Long): Group?

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupByIdFlow(groupId: Long): Flow<Group?>

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupWithCards(groupId: Long): Flow<GroupWithCards?>

    @Transaction
    @Query("SELECT * FROM groups ORDER BY name ASC")
    fun getAllGroupsWithCards(): Flow<List<GroupWithCards>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group): Long

    @Update
    suspend fun updateGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    // Junction table operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardGroupCrossRef(crossRef: CardGroupCrossRef)

    @Delete
    suspend fun deleteCardGroupCrossRef(crossRef: CardGroupCrossRef)

    @Query("DELETE FROM card_group_cross_ref WHERE cardId = :cardId")
    suspend fun deleteAllGroupsForCard(cardId: Long)

    @Query("DELETE FROM card_group_cross_ref WHERE groupId = :groupId")
    suspend fun deleteAllCardsForGroup(groupId: Long)

    @Query("SELECT COUNT(*) FROM groups")
    fun getGroupCount(): Flow<Int>

    // Get groups for a specific card
    @Query("""
        SELECT g.* FROM groups g
        INNER JOIN card_group_cross_ref cgc ON g.id = cgc.groupId
        WHERE cgc.cardId = :cardId
        ORDER BY g.name ASC
    """)
    fun getGroupsForCard(cardId: Long): Flow<List<Group>>

    @Query("""
        SELECT g.* FROM groups g
        INNER JOIN card_group_cross_ref cgc ON g.id = cgc.groupId
        WHERE cgc.cardId = :cardId
        ORDER BY g.name ASC
    """)
    suspend fun getGroupsForCardSync(cardId: Long): List<Group>

    @Query("SELECT * FROM groups WHERE name = :name LIMIT 1")
    suspend fun getGroupByName(name: String): Group?

    // Learning groups
    @Query("SELECT * FROM learning_groups ORDER BY name COLLATE NOCASE ASC")
    fun getAllLearningGroups(): Flow<List<LearningGroup>>

    @Query("SELECT * FROM learning_groups WHERE id = :learningGroupId")
    suspend fun getLearningGroupById(learningGroupId: Long): LearningGroup?

    @Query("SELECT * FROM learning_groups WHERE name = :name LIMIT 1")
    suspend fun getLearningGroupByName(name: String): LearningGroup?

    @Insert
    suspend fun insertLearningGroup(learningGroup: LearningGroup): Long

    @Update
    suspend fun updateLearningGroup(learningGroup: LearningGroup)

    @Delete
    suspend fun deleteLearningGroup(learningGroup: LearningGroup)

    @Query("SELECT COUNT(DISTINCT cardId) FROM card_group_cross_ref WHERE groupId IN (:groupIds)")
    suspend fun countDistinctCardsInGroups(groupIds: List<Long>): Int

    @Query("UPDATE groups SET learningGroupId = :learningGroupId WHERE id = :groupId")
    suspend fun setLearningGroupForGroup(groupId: Long, learningGroupId: Long?)

    // Learning state operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearningState(learningState: CardLearningState)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLearningStateIfAbsent(learningState: CardLearningState)

    @Update
    suspend fun updateLearningState(learningState: CardLearningState)

    @Query("SELECT * FROM card_learning_state WHERE cardId = :cardId AND learningGroupId = :learningGroupId")
    suspend fun getLearningState(cardId: Long, learningGroupId: Long): CardLearningState?

    @Query("SELECT * FROM card_learning_state WHERE cardId = :cardId")
    suspend fun getAllLearningStatesForCard(cardId: Long): List<CardLearningState>

    @Query("SELECT * FROM card_learning_state WHERE cardId = :cardId")
    fun getAllLearningStatesForCardFlow(cardId: Long): Flow<List<CardLearningState>>

    @Query("DELETE FROM card_learning_state WHERE cardId = :cardId")
    suspend fun deleteAllLearningStatesForCard(cardId: Long)

    // Whole-table reads, for reports that look at every card in every group
    @Query("SELECT * FROM card_learning_state")
    fun getAllLearningStates(): Flow<List<CardLearningState>>

    @Query("SELECT * FROM card_group_cross_ref")
    fun getAllCardGroupCrossRefs(): Flow<List<CardGroupCrossRef>>
}
