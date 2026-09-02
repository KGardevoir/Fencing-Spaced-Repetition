// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.*
import com.fencing.spacedrepetition.data.model.PracticeHistoryStats
import com.fencing.spacedrepetition.data.model.ReviewLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {
    @Query("SELECT * FROM review_logs ORDER BY reviewTime DESC")
    fun getAllReviewLogs(): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_logs ORDER BY reviewTime DESC")
    suspend fun getAllReviewLogsSync(): List<ReviewLog>

    @Query("SELECT * FROM review_logs WHERE cardId = :cardId ORDER BY reviewTime DESC")
    fun getReviewLogsByCard(cardId: Long): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_logs WHERE sessionId = :sessionId ORDER BY reviewTime ASC")
    fun getReviewLogsBySession(sessionId: Long): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_logs WHERE sessionId IS NULL ORDER BY reviewTime DESC")
    fun getReviewLogsWithoutSession(): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_logs WHERE reviewTime >= :startTime AND reviewTime <= :endTime ORDER BY reviewTime DESC")
    fun getReviewLogsByDateRange(startTime: Long, endTime: Long): Flow<List<ReviewLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewLog(reviewLog: ReviewLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewLogs(reviewLogs: List<ReviewLog>)

    @Update
    suspend fun updateReviewLog(reviewLog: ReviewLog)

    @Delete
    suspend fun deleteReviewLog(reviewLog: ReviewLog)

    @Query("DELETE FROM review_logs WHERE cardId = :cardId")
    suspend fun deleteReviewLogsByCard(cardId: Long)

    @Query("SELECT COUNT(*) FROM review_logs")
    fun getReviewLogCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewTime >= :startTime")
    fun getReviewCountSince(startTime: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) AS totalReviews,
               COUNT(DISTINCT date(reviewTime / 1000, 'unixepoch', 'localtime')) AS practiceDays,
               COALESCE(MIN(reviewTime), 0) AS firstReviewTime
        FROM review_logs
        WHERE reviewTime >= :startTime
        """
    )
    fun getPracticeHistoryStats(startTime: Long): Flow<PracticeHistoryStats>
}
