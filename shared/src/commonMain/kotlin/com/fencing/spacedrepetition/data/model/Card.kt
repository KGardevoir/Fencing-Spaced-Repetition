// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.fencing.spacedrepetition.util.Time

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

    // FSRS fields
    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Int = 0,
    val fsrsScheduledDays: Int = 0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: String = "NEW", // NEW, LEARNING, REVIEW, RELEARNING

    // Common fields
    val lastReview: Long = 0L,
    val nextReview: Long = 0L,
    val created: Long = Time.now(),
    val modified: Long = Time.now(),
    val isDisabled: Boolean = false
)
