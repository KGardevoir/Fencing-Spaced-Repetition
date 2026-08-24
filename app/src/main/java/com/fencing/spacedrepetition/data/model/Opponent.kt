// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fencing.spacedrepetition.util.Time

/**
 * A training partner / opponent whose skill level modulates the stability gain
 * applied to a successful FSRS review. A multiplier of 1.0 is neutral (no effect);
 * > 1.0 earns more stability (harder opponent), < 1.0 earns less (weaker opponent).
 */
@Entity(
    tableName = "opponents",
    indices = [Index(value = ["name"], unique = true)]
)
data class Opponent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** Applied to the FSRS stability gain for non-AGAIN grades. Typical range 0.5–2.0. */
    val skillMultiplier: Double = 1.0,

    val notes: String = "",

    val created: Long = Time.now(),
    val modified: Long = Time.now()
)
