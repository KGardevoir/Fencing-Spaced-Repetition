package com.fencing.spacedrepetition.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.GroupWithCards
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
}
