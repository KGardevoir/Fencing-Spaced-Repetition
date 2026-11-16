// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.fencing.spacedrepetition.util.Time
import androidx.room3.ColumnInfo
import androidx.room3.Index
import com.fencing.spacedrepetition.util.newUid

@Entity(tableName = "review_logs", indices = [Index(value = ["uid"], unique = true)])
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
    // "card_edit" = graded from the Add/Edit card screen. The name as it was at
    // the time, for display; which group it was is [groupUid].
    val groupName: String? = null,

    // User notes (markdown) and attached images for this review
    val notes: String = "",
    val imagePaths: String = "", // Comma-separated file paths

    // Opponent this review was performed against (null = solo / unspecified).
    // Soft reference: not a FK, so deleting an opponent leaves historical logs
    // intact. [opponentUid] is who it was; this is where they are locally.
    val opponentId: Long? = null,

    // How hard the fight itself was, 1-5 (null = unrated). Scales the stability
    // gain alongside the opponent's skill level; see FightDifficulty.
    val fightDifficulty: Int? = null,

    // Stability-gain multiplier actually applied to this review (1.0 = neutral):
    // the opponent's skill multiplier times the fight difficulty's multiplier.
    // Recorded on the log so past reviews stay faithful even if the opponent's
    // skill multiplier is later edited.
    val stabilityMultiplier: Double = 1.0,

    /**
     * The stable identifier of the card group the review was made in, or null
     * for none (all-cards practice, the card editor without a group). What
     * places the review in a learning context; unlike [groupName] it survives
     * the group being renamed.
     */
    val groupUid: String? = null,

    /**
     * The stable identifier of the opponent the review was against, or null.
     * Kept when the opponent is deleted, so re-adding them (by import) puts
     * the review back with them, and it is what an export names them by.
     */
    val opponentUid: String? = null,
    /** Stable identifier, unique and kept for life; see docs/DESIGN.md, "Identity". */
    @ColumnInfo(defaultValue = "''")
    val uid: String = newUid()
)
