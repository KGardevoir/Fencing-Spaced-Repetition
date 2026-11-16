// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.fencing.spacedrepetition.util.Time
import androidx.room3.ColumnInfo
import com.fencing.spacedrepetition.util.newUid

/**
 * A learning concept: the thing that is actually being learnt, which cards
 * are tagged with.
 *
 * Concepts are global -- one "Parry 4" whatever groups its cards are in --
 * but their learning progress is not. Like a card, a concept keeps its Global
 * state on itself and one more state per [LearningGroup] in
 * [ConceptLearningState], so a group practising in a learning group moves on
 * that learning group's progress of the concept and nobody else's.
 *
 * A concept's state is derived, never accumulated: it is the replay of the
 * review history of the cards that carry it, per learning context (see
 * CardRepository.rederiveConcepts). What is stored here is that result.
 *
 * Where a learning context schedules by concept, a session draws the
 * concepts that are due and picks the cards that cover the most of them.
 */
@Entity(
    tableName = "concepts",
    indices = [Index(value = ["name"], unique = true), Index(value = ["uid"], unique = true)]
)
data class Concept(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",

    // Global FSRS state
    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Int = 0,
    val fsrsScheduledDays: Int = 0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: String = "NEW",
    val lastReview: Long = 0L,
    val nextReview: Long = 0L,

    val created: Long = Time.now(),
    val modified: Long = Time.now(),
    /** Stable identifier, unique and kept for life; see docs/DESIGN.md, "Identity". */
    @ColumnInfo(defaultValue = "''")
    val uid: String = newUid()
) {
    /** The Global state, in the same shape a learning group's state has. */
    fun globalState(): ConceptLearningState = ConceptLearningState(
        conceptId = id,
        learningGroupId = 0L,
        fsrsStability = fsrsStability,
        fsrsDifficulty = fsrsDifficulty,
        fsrsElapsedDays = fsrsElapsedDays,
        fsrsScheduledDays = fsrsScheduledDays,
        fsrsReps = fsrsReps,
        fsrsLapses = fsrsLapses,
        fsrsState = fsrsState,
        lastReview = lastReview,
        nextReview = nextReview,
        modified = modified
    )

    /** This concept with [state] written back as its Global state. */
    fun withGlobalState(state: ConceptLearningState): Concept = copy(
        fsrsStability = state.fsrsStability,
        fsrsDifficulty = state.fsrsDifficulty,
        fsrsElapsedDays = state.fsrsElapsedDays,
        fsrsScheduledDays = state.fsrsScheduledDays,
        fsrsReps = state.fsrsReps,
        fsrsLapses = state.fsrsLapses,
        fsrsState = state.fsrsState,
        lastReview = state.lastReview,
        nextReview = state.nextReview,
        modified = state.modified
    )
}

/** Which concepts a card is tagged with. */
@Entity(
    tableName = "card_concept_cross_ref",
    primaryKeys = ["cardId", "conceptId"],
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Concept::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conceptId")]
)
data class CardConceptCrossRef(
    val cardId: Long,
    val conceptId: Long
)

/** One concept's learning progress in one [LearningGroup]. */
@Entity(
    tableName = "concept_learning_state",
    primaryKeys = ["conceptId", "learningGroupId"],
    foreignKeys = [
        ForeignKey(
            entity = Concept::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearningGroup::class,
            parentColumns = ["id"],
            childColumns = ["learningGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("learningGroupId"), Index("conceptId")]
)
data class ConceptLearningState(
    val conceptId: Long,
    val learningGroupId: Long,

    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Int = 0,
    val fsrsScheduledDays: Int = 0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: String = "NEW",
    val lastReview: Long = 0L,
    val nextReview: Long = 0L,
    val modified: Long = Time.now()
)
