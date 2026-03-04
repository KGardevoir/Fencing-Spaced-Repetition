package com.fencing.spacedrepetition.data.dao

import androidx.room.*
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

    @Delete
    suspend fun deleteReviewLog(reviewLog: ReviewLog)

    @Query("DELETE FROM review_logs WHERE cardId = :cardId")
    suspend fun deleteReviewLogsByCard(cardId: Long)

    @Query("DELETE FROM review_logs")
    suspend fun deleteAllReviewLogs()

    @Query("SELECT COUNT(*) FROM review_logs")
    fun getReviewLogCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewTime >= :startTime")
    fun getReviewCountSince(startTime: Long): Flow<Int>
}
