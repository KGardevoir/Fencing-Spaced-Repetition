// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * One set of FSRS learning progress, whoever holds it: a card's own (Global)
 * progress, a card's progress in a learning group ([CardLearningState]), or a
 * concept's ([Concept], [ConceptLearningState]). Each keeps these nine values
 * in its own columns; this is them as one value, so scheduling, logging and
 * copying work the same on all of them.
 */
data class FsrsState(
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: String = "NEW",
    val lastReview: Long = 0L,
    val nextReview: Long = 0L
) {
    /** As a review log records a state before and after: `S:3.1,D:5.2,ST:REVIEW`. */
    fun serialized(): String = "S:$stability,D:$difficulty,ST:$state"

    /** Whole days since the last review at [now]; 0 if never reviewed. */
    fun daysSinceReview(now: Long): Int = if (lastReview == 0L) 0 else ((now - lastReview) / DAY_MILLIS).toInt()

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

val Card.fsrs: FsrsState
    get() = FsrsState(fsrsStability, fsrsDifficulty, fsrsElapsedDays, fsrsScheduledDays, fsrsReps, fsrsLapses, fsrsState, lastReview, nextReview)

/** This card with [state] as its own (Global) progress. */
fun Card.withFsrs(state: FsrsState): Card = copy(
    fsrsStability = state.stability, fsrsDifficulty = state.difficulty, fsrsElapsedDays = state.elapsedDays,
    fsrsScheduledDays = state.scheduledDays, fsrsReps = state.reps, fsrsLapses = state.lapses,
    fsrsState = state.state, lastReview = state.lastReview, nextReview = state.nextReview
)

val CardLearningState.fsrs: FsrsState
    get() = FsrsState(fsrsStability, fsrsDifficulty, fsrsElapsedDays, fsrsScheduledDays, fsrsReps, fsrsLapses, fsrsState, lastReview, nextReview)

fun CardLearningState.withFsrs(state: FsrsState): CardLearningState = copy(
    fsrsStability = state.stability, fsrsDifficulty = state.difficulty, fsrsElapsedDays = state.elapsedDays,
    fsrsScheduledDays = state.scheduledDays, fsrsReps = state.reps, fsrsLapses = state.lapses,
    fsrsState = state.state, lastReview = state.lastReview, nextReview = state.nextReview
)

val ConceptLearningState.fsrs: FsrsState
    get() = FsrsState(fsrsStability, fsrsDifficulty, fsrsElapsedDays, fsrsScheduledDays, fsrsReps, fsrsLapses, fsrsState, lastReview, nextReview)

fun ConceptLearningState.withFsrs(state: FsrsState): ConceptLearningState = copy(
    fsrsStability = state.stability, fsrsDifficulty = state.difficulty, fsrsElapsedDays = state.elapsedDays,
    fsrsScheduledDays = state.scheduledDays, fsrsReps = state.reps, fsrsLapses = state.lapses,
    fsrsState = state.state, lastReview = state.lastReview, nextReview = state.nextReview
)
