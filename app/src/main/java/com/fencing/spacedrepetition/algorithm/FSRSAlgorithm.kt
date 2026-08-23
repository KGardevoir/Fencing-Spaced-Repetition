// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.algorithm

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

/**
 * FSRS (Free Spaced Repetition Scheduler) Algorithm Implementation
 * Based on the FSRS-6 specification
 *
 * Key changes from FSRS-5:
 * - 21 parameters (was 19); w[19] and w[20] added
 * - Trainable forgetting-curve decay: DECAY = -w[20], FACTOR = 0.9^(-1/w[20]) - 1
 * - Short-term stability (same-day): multiplied by stability^(-w[19]), causing the
 *   increment to slow down as stability grows
 * - Optional interval fuzzing to prevent review pile-ups
 */
class FSRSAlgorithm(
    private var maximumInterval: Int = 36500 // Default: 100 years
) {

    data class FSRSCard(
        val stability: Double = 0.0,
        val difficulty: Double = 0.0,
        val elapsedDays: Int = 0,
        val scheduledDays: Int = 0,
        val reps: Int = 0,
        val lapses: Int = 0,
        val state: CardState = CardState.NEW,
        val lastReview: Long = 0L
    )

    enum class CardState {
        NEW, LEARNING, REVIEW, RELEARNING
    }

    enum class Rating {
        AGAIN,    // 1 - Complete failure
        HARD,     // 2 - Difficult recall
        GOOD,     // 3 - Correct with effort
        EASY      // 4 - Perfect recall
    }

    data class SchedulingInfo(
        val card: FSRSCard,
        val reviewLog: ReviewLog
    )

    data class ReviewLog(
        val rating: Rating,
        val scheduledDays: Int,
        val elapsedDays: Int,
        val review: Long,
        val state: CardState
    )

    // FSRS-6 Parameters (optimized defaults, 21 values)
    private val w = doubleArrayOf(
        0.212,  1.2931, 2.3065, 8.2956, 6.4133,
        0.8334, 3.0194, 0.001,  1.8722, 0.1666,
        0.796,  1.4835, 0.0614, 0.2629, 1.6483,
        0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
        0.1542
    )

    private var requestRetention = 0.9 // Target retention rate (0.70–0.97)
    private var enableFuzzing = false   // Add small random variance to intervals

    // FSRS-6: DECAY is trainable (-w[20]).
    // FACTOR is derived so R(S, S) = 90 % (the curve is anchored at the 90 % point):
    //   (1 + FACTOR)^DECAY = 0.9  →  FACTOR = 0.9^(1/DECAY) - 1 = 0.9^(-1/w[20]) - 1
    private fun getDecay(): Double = -w[20]
    private fun getFactor(): Double = 0.9.pow(-1.0 / w[20]) - 1

    /**
     * Update the maximum interval setting.
     */
    fun setMaximumInterval(days: Int) {
        maximumInterval = days.coerceAtLeast(1)
    }

    /**
     * Update the desired retention rate.
     * @param retentionPercent Integer percentage, e.g. 90 for 90 % (0.90).
     */
    fun setRequestRetention(retentionPercent: Int) {
        requestRetention = (retentionPercent / 100.0).coerceIn(0.1, 0.99)
    }

    /**
     * Enable or disable interval fuzzing.
     * When enabled, a small random offset (≤ 5 % of the interval, min 1 day) is
     * added to each computed review interval to prevent review pile-ups.
     */
    fun setEnableFuzzing(enable: Boolean) {
        enableFuzzing = enable
    }

    /**
     * Schedule a card review based on rating.
     *
     * @param stabilityMultiplier Multiplicative scale applied only to the *stability gain*
     *   for non-AGAIN grades. 1.0 is neutral; > 1.0 earns more stability (e.g. a strong
     *   opponent), < 1.0 earns less. Initial seeding (NEW) and lapses (AGAIN) are unaffected.
     */
    fun schedule(
        card: FSRSCard,
        rating: Rating,
        now: Long = System.currentTimeMillis(),
        stabilityMultiplier: Double = 1.0
    ): SchedulingInfo {
        val elapsedDays = if (card.lastReview == 0L) {
            0
        } else {
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        }

        val newCard = when (card.state) {
            CardState.NEW -> scheduleNew(card, rating)
            CardState.LEARNING, CardState.RELEARNING -> scheduleLearning(card, rating, stabilityMultiplier)
            CardState.REVIEW -> scheduleReview(card, rating, elapsedDays, stabilityMultiplier)
        }

        val reviewLog = ReviewLog(
            rating = rating,
            scheduledDays = newCard.scheduledDays,
            elapsedDays = elapsedDays,
            review = now,
            state = newCard.state
        )

        return SchedulingInfo(newCard.copy(lastReview = now), reviewLog)
    }

    /**
     * Get all possible scheduling outcomes for a card.
     */
    fun getSchedulingCards(card: FSRSCard, now: Long = System.currentTimeMillis()): Map<Rating, FSRSCard> {
        return Rating.values().associateWith { rating ->
            schedule(card, rating, now).card
        }
    }

    private fun scheduleNew(card: FSRSCard, rating: Rating): FSRSCard {
        val difficulty = initDifficulty(rating)
        val stability = initStability(rating)

        return when (rating) {
            Rating.AGAIN -> card.copy(
                difficulty = difficulty,
                stability = stability,
                state = CardState.LEARNING,
                scheduledDays = 0,
                reps = 1
            )
            Rating.HARD -> card.copy(
                difficulty = difficulty,
                stability = stability,
                state = CardState.LEARNING,
                scheduledDays = 0,
                reps = 1
            )
            Rating.GOOD -> card.copy(
                difficulty = difficulty,
                stability = stability,
                state = CardState.LEARNING,
                scheduledDays = 1,
                reps = 1
            )
            Rating.EASY -> card.copy(
                difficulty = difficulty,
                stability = stability,
                state = CardState.REVIEW,
                scheduledDays = nextInterval(stability),
                reps = 1
            )
        }
    }

    private fun scheduleLearning(card: FSRSCard, rating: Rating, stabilityMultiplier: Double): FSRSCard {
        // Only scale the gain for successful recalls; AGAIN keeps neutral scaling.
        val mult = if (rating == Rating.AGAIN) 1.0 else stabilityMultiplier
        return when (rating) {
            Rating.AGAIN -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.AGAIN)
                val newStab = shortTermStability(card.stability, rating, mult)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.LEARNING,
                    scheduledDays = 0,
                    reps = card.reps + 1
                )
            }
            Rating.HARD -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.HARD)
                val newStab = shortTermStability(card.stability, rating, mult)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.LEARNING,
                    scheduledDays = 0,
                    reps = card.reps + 1
                )
            }
            Rating.GOOD -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.GOOD)
                val newStab = shortTermStability(card.stability, rating, mult)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = nextInterval(newStab),
                    reps = card.reps + 1
                )
            }
            Rating.EASY -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.EASY)
                val newStab = shortTermStability(card.stability, rating, mult)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = nextInterval(newStab),
                    reps = card.reps + 1
                )
            }
        }
    }

    private fun scheduleReview(card: FSRSCard, rating: Rating, elapsedDays: Int, stabilityMultiplier: Double): FSRSCard {
        val retrievability = forgettingCurve(elapsedDays, card.stability)

        return when (rating) {
            Rating.AGAIN -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.AGAIN)
                // Lapses are unaffected by opponent skill.
                val newStab = nextForgetStability(card.difficulty, card.stability, retrievability)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.RELEARNING,
                    scheduledDays = 0,
                    reps = card.reps + 1,
                    lapses = card.lapses + 1
                )
            }
            Rating.HARD -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.HARD)
                val newStab = nextRecallStability(card.difficulty, card.stability, retrievability, rating, stabilityMultiplier)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = nextInterval(newStab),
                    reps = card.reps + 1
                )
            }
            Rating.GOOD -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.GOOD)
                val newStab = nextRecallStability(card.difficulty, card.stability, retrievability, rating, stabilityMultiplier)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = nextInterval(newStab),
                    reps = card.reps + 1
                )
            }
            Rating.EASY -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.EASY)
                val newStab = nextRecallStability(card.difficulty, card.stability, retrievability, rating, stabilityMultiplier)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = nextInterval(newStab),
                    reps = card.reps + 1
                )
            }
        }
    }

    private fun initStability(rating: Rating): Double {
        return when (rating) {
            Rating.AGAIN -> w[0]
            Rating.HARD  -> w[1]
            Rating.GOOD  -> w[2]
            Rating.EASY  -> w[3]
        }
    }

    private fun initDifficulty(rating: Rating): Double {
        val g = rating.ordinal + 1  // 1-indexed grade (AGAIN=1, HARD=2, GOOD=3, EASY=4)
        return (w[4] - exp(w[5] * (g - 1)) + 1).coerceIn(1.0, 10.0)
    }

    private fun forgettingCurve(elapsedDays: Int, stability: Double): Double {
        // FSRS-6: R(t, S) = (1 + factor * t / S)^(-w[20])
        return (1.0 + getFactor() * elapsedDays / stability).pow(getDecay())
    }

    private fun nextInterval(stability: Double): Int {
        // Solve R(t, S) = requestRetention for t:
        //   t = S / factor * (requestRetention^(1/decay) - 1)
        val factor = getFactor()
        val decay = getDecay()
        val rawInterval = (stability / factor * (requestRetention.pow(1.0 / decay) - 1)).toInt()
            .coerceIn(1, maximumInterval)
        return if (enableFuzzing) applyFuzz(rawInterval) else rawInterval
    }

    /**
     * Add a small random offset to [interval] to prevent review pile-ups.
     * Fuzz is ≤ 5 % of the interval (minimum ±1 day for intervals ≥ 3).
     */
    private fun applyFuzz(interval: Int): Int {
        if (interval < 3) return interval
        val delta = max(1, round(interval * 0.05).toInt())
        return (interval + Random.nextInt(-delta, delta + 1)).coerceIn(1, maximumInterval)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val g = rating.ordinal + 1  // 1-indexed grade (AGAIN=1, HARD=2, GOOD=3, EASY=4)
        val delta = -w[6] * (g - 3)
        val newDifficulty = difficulty + delta * (10.0 - difficulty) / 9.0
        return meanReversion(initDifficulty(Rating.EASY), newDifficulty).coerceIn(1.0, 10.0)
    }

    private fun meanReversion(init: Double, current: Double): Double {
        return w[7] * init + (1.0 - w[7]) * current
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
        stabilityMultiplier: Double = 1.0
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus  = if (rating == Rating.EASY) w[16] else 1.0

        val gainFactor = exp(w[8]) *
            (11 - difficulty) *
            stability.pow(-w[9]) *
            (exp((1 - retrievability) * w[10]) - 1) *
            hardPenalty *
            easyBonus
        // The opponent-skill multiplier scales only the gain, not the base stability.
        return stability * (1 + gainFactor * stabilityMultiplier)
    }

    /**
     * FSRS-6 short-term stability (same-day review in LEARNING / RELEARNING).
     *
     * S'_sn(S, G) = S · exp(w[17] · (G − 3 + w[18])) · S^(−w[19])
     *
     * The S^(−w[19]) term causes the stability increment to slow down as stability grows,
     * converging to Δ ≈ 1 day, so same-day reviews are most valuable for weak memories.
     */
    private fun shortTermStability(stability: Double, rating: Rating, stabilityMultiplier: Double = 1.0): Double {
        val g = rating.ordinal + 1 // 1-indexed: AGAIN=1, HARD=2, GOOD=3, EASY=4
        val s = stability.coerceAtLeast(0.001) // guard against zero-stability edge case
        val newStab = s * exp(w[17] * (g - 3 + w[18])) * s.pow(-w[19])
        // Scale only the delta so the multiplier affects the *gain*, not the base.
        return s + (newStab - s) * stabilityMultiplier
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        return w[11] * difficulty.pow(-w[12]) *
               ((stability + 1).pow(w[13]) - 1) *
               exp((1 - retrievability) * w[14])
    }
}
