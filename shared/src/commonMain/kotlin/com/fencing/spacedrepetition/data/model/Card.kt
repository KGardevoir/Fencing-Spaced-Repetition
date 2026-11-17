// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.fencing.spacedrepetition.util.Time
import androidx.room3.ColumnInfo
import androidx.room3.Index
import com.fencing.spacedrepetition.util.newUid

@Entity(tableName = "cards", indices = [Index(value = ["uid"], unique = true)])
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
    val isDisabled: Boolean = false,
    /**
     * When the card's own (Global) progress was last reset, or 0. Reviews
     * before it are history, not progress: they stay in the review log but no
     * longer count towards anything derived from it, such as concepts.
     */
    @ColumnInfo(defaultValue = "0")
    val lastReset: Long = 0L,
    /** Stable identifier, unique and kept for life; see docs/DESIGN.md, "Identity". */
    @ColumnInfo(defaultValue = "''")
    val uid: String = newUid()
)
