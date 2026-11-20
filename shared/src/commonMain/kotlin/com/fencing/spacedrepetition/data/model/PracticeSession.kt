// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.fencing.spacedrepetition.util.Time
import androidx.room3.ColumnInfo
import androidx.room3.Index
import com.fencing.spacedrepetition.util.newUid

@Entity(tableName = "practice_sessions", indices = [Index(value = ["uid"], unique = true)])
data class PracticeSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val startTime: Long = Time.now(),
    val endTime: Long? = null,
    val completed: Boolean = false,

    // Card IDs in this session (comma-separated)
    val cardIds: String = "",

    // Grades assigned (comma-separated, corresponding to cardIds)
    // Empty until session is completed
    val grades: String = "",

    // Notes on the fight (markdown) and the images attached to them. One set
    // per session rather than one per card: like the opponent and the fight's
    // difficulty, what is noted is about the fight, and the session is the
    // fight. Quick grades from the card editor are not in a session and keep
    // their notes on their own review log.
    val notes: String = "",
    val imagePaths: String = "", // Comma-separated image keys
    /** Stable identifier, unique and kept for life; see docs/DESIGN.md, "Identity". */
    @ColumnInfo(defaultValue = "''")
    val uid: String = newUid()
)

/**
 * A card and its grade during a practice session, with why it was drawn.
 *
 * Carries nothing about the opponent, how hard the fight was, or what was
 * noted about it. Those describe the fight, and the session is the fight: they
 * are held once for the session. A copy per card could disagree with the
 * session it belongs to, so there is not one.
 *
 * @param selection why the card was drawn; written on its review log.
 * @param concepts the card's concepts with how much of each it contains, for
 *   a session drawn by concept; empty otherwise.
 */
data class SessionCard(
    val card: Card,
    val grade: Grade? = null,
    val selection: CardSelection? = null,
    val concepts: List<Pair<Concept, Double>> = emptyList()
)

enum class Grade(val value: Int, val label: String) {
    SKIP(0, "Skip"),
    AGAIN(1, "Again"),
    HARD(2, "Hard"),
    GOOD(3, "Good"),
    EASY(4, "Easy");

    companion object {
        fun fromValue(value: Int): Grade? = values().find { it.value == value }
    }
}
