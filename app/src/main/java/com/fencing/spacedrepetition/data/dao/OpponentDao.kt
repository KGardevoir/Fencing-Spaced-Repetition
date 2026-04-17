package com.fencing.spacedrepetition.data.dao

import androidx.room.*
import com.fencing.spacedrepetition.data.model.Opponent
import kotlinx.coroutines.flow.Flow

@Dao
interface OpponentDao {
    @Query("SELECT * FROM opponents ORDER BY name COLLATE NOCASE ASC")
    fun getAllOpponents(): Flow<List<Opponent>>

    @Query("SELECT * FROM opponents ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllOpponentsSync(): List<Opponent>

    @Query("SELECT * FROM opponents WHERE id = :id")
    suspend fun getOpponentById(id: Long): Opponent?

    @Query("SELECT * FROM opponents WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Opponent?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOpponent(opponent: Opponent): Long

    @Update
    suspend fun updateOpponent(opponent: Opponent)

    @Delete
    suspend fun deleteOpponent(opponent: Opponent)

    @Query("DELETE FROM opponents WHERE id = :id")
    suspend fun deleteOpponentById(id: Long)

    @Query("SELECT COUNT(*) FROM opponents")
    fun getOpponentCount(): Flow<Int>
}
