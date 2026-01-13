package com.fencing.spacedrepetition.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Card content
    val question: String,
    val answer: String,
    val category: String = "",
    val tags: String = "", // Comma-separated tags
    val imagePaths: List<String> = emptyList(), // List of image file paths/URIs

    // Algorithm selection
    val algorithm: AlgorithmType = AlgorithmType.FSRS,

    // FSRS fields
    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Int = 0,
    val fsrsScheduledDays: Int = 0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: String = "NEW", // NEW, LEARNING, REVIEW, RELEARNING

    // SM-2 fields
    val sm2EaseFactor: Double = 2.5,
    val sm2Interval: Int = 0,
    val sm2Repetitions: Int = 0,

    // Common fields
    val lastReview: Long = 0L,
    val nextReview: Long = 0L,
    val created: Long = System.currentTimeMillis(),
    val modified: Long = System.currentTimeMillis()
)

enum class AlgorithmType {
    FSRS, SM2
}
