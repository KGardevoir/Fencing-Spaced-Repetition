// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.*
import com.fencing.spacedrepetition.data.model.PracticeSession
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {

    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): PracticeSession?

    @Query("SELECT * FROM practice_sessions WHERE id = :sessionId")
    fun getSessionByIdFlow(sessionId: Long): Flow<PracticeSession?>

    @Query("SELECT * FROM practice_sessions WHERE completed = 1 ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<PracticeSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSession): Long

    @Update
    suspend fun updateSession(session: PracticeSession)

    @Query("SELECT * FROM practice_sessions ORDER BY startTime DESC")
    suspend fun getAllSessionsSync(): List<PracticeSession>

    @Query("SELECT * FROM practice_sessions WHERE uid = :uid LIMIT 1")
    suspend fun getSessionByUid(uid: String): PracticeSession?

    @Query("SELECT * FROM practice_sessions WHERE startTime = :startTime LIMIT 1")
    suspend fun getSessionByStartTime(startTime: Long): PracticeSession?

}
