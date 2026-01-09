package com.fencing.spacedrepetition.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_logs")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val cardId: Long,
    val sessionId: Long?,
    val reviewTime: Long = System.currentTimeMillis(),

    val grade: Int, // 1-4 (AGAIN, HARD, GOOD, EASY)
    val algorithm: String, // "FSRS" or "SM2"

    // State before review
    val stateBefore: String,

    // State after review
    val stateAfter: String,

    // Interval info
    val scheduledDays: Int,
    val elapsedDays: Int
)
