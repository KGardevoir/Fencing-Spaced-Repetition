// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.fencing.spacedrepetition.util.Time

@Entity(tableName = "review_logs")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val cardId: Long,
    val sessionId: Long?,
    val reviewTime: Long = Time.now(),

    val grade: Int, // 1-4 (AGAIN, HARD, GOOD, EASY)
    // Scheduler that produced this review. Always "FSRS" for new logs; older
    // rows may say "SM2", from before that algorithm was removed.
    val algorithm: String,

    // State before review
    val stateBefore: String,

    // State after review
    val stateAfter: String,

    // Interval info
    val scheduledDays: Int,
    val elapsedDays: Int,

    // Source context: null = all-cards practice, group name = within-group practice,
    // "card_edit" = graded from the Add/Edit card screen
    val groupName: String? = null,

    // User notes (markdown) and attached images for this review
    val notes: String = "",
    val imagePaths: String = "", // Comma-separated file paths

    // Opponent this review was performed against (null = solo / unspecified).
    // Soft reference: not a FK, so deleting an opponent leaves historical logs intact.
    val opponentId: Long? = null,

    // Stability-gain multiplier applied by the opponent's skill level (1.0 = neutral).
    // Recorded on the log so past reviews stay faithful even if the opponent's
    // skill multiplier is later edited.
    val stabilityMultiplier: Double = 1.0
)
