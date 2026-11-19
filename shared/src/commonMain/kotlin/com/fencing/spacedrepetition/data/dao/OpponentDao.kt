// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.*
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

    @Query("SELECT * FROM opponents WHERE uid = :uid LIMIT 1")
    suspend fun findByUid(uid: String): Opponent?

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

    /**
     * Point every review fought against [fromId] -- by local id or by its
     * stable identifier [fromUid] -- at [toId] and [toUid] instead.
     */
    @Query("""
        UPDATE review_logs SET opponentId = :toId, opponentUid = :toUid
        WHERE opponentId = :fromId OR opponentUid = :fromUid
    """)
    suspend fun reassignReviewLogs(fromId: Long, toId: Long, fromUid: String, toUid: String)

}
