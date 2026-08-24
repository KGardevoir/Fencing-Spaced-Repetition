// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.*
import com.fencing.spacedrepetition.data.model.PracticeSession
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<PracticeSession>>

    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): PracticeSession?

    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId")
    fun getSessionByIdFlow(sessionId: Long): Flow<PracticeSession?>

    @Query("SELECT * FROM practice_sessions WHERE completed = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): PracticeSession?

    @Query("SELECT * FROM practice_sessions WHERE completed = 0 ORDER BY startTime DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<PracticeSession?>

    @Query("SELECT * FROM practice_sessions WHERE completed = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<PracticeSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSession): Long

    @Update
    suspend fun updateSession(session: PracticeSession)

    @Delete
    suspend fun deleteSession(session: PracticeSession)

    @Query("DELETE FROM practice_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("SELECT COUNT(*) FROM practice_sessions WHERE completed = 1")
    fun getCompletedSessionCount(): Flow<Int>
}
