package com.fencing.spacedrepetition.algorithm

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * FSRS (Free Spaced Repetition Scheduler) Algorithm Implementation
 * Based on the FSRS-4.5 specification
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

    // FSRS Parameters (optimized defaults)
    private val w = doubleArrayOf(
        0.4072, 1.1829, 3.1262, 15.4722, 7.2102,
        0.5316, 1.0651, 0.0234, 1.616, 0.1544,
        1.0824, 1.9813, 0.0953, 0.2975, 2.2042,
        0.2407, 2.9466, 0.5034, 0.6567
    )

    private val requestRetention = 0.9 // Target retention rate
    private val easyBonus = 1.3
    private val hardInterval = 1.2

    /**
     * Update the maximum interval setting
     */
    fun setMaximumInterval(days: Int) {
        maximumInterval = days.coerceAtLeast(1)
    }

    /**
     * Schedule a card review based on rating
     */
    fun schedule(card: FSRSCard, rating: Rating, now: Long = System.currentTimeMillis()): SchedulingInfo {
        val elapsedDays = if (card.lastReview == 0L) {
            0
        } else {
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        }

        val newCard = when (card.state) {
            CardState.NEW -> scheduleNew(card, rating)
            CardState.LEARNING, CardState.RELEARNING -> scheduleLearning(card, rating)
            CardState.REVIEW -> scheduleReview(card, rating, elapsedDays)
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
     * Get all possible scheduling outcomes for a card
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
                reps = 1,
                lapses = card.lapses + 1
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
                scheduledDays = (stability * easyBonus).toInt().coerceAtMost(maximumInterval),
                reps = 1
            )
        }
    }

    private fun scheduleLearning(card: FSRSCard, rating: Rating): FSRSCard {
        val newStability = when (rating) {
            Rating.AGAIN -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.AGAIN)
                card.copy(
                    difficulty = newDifficulty,
                    stability = card.stability,
                    state = CardState.LEARNING,
                    scheduledDays = 0,
                    reps = card.reps + 1,
                    lapses = card.lapses + 1
                )
            }
            Rating.HARD -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.HARD)
                val newStab = nextStability(card.difficulty, card.stability, 1.0, rating)
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
                val newStab = nextStability(card.difficulty, card.stability, 1.0, rating)
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
                val newStab = nextStability(card.difficulty, card.stability, 1.0, rating)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = (nextInterval(newStab) * easyBonus).toInt().coerceAtMost(maximumInterval),
                    reps = card.reps + 1
                )
            }
        }
        return newStability
    }

    private fun scheduleReview(card: FSRSCard, rating: Rating, elapsedDays: Int): FSRSCard {
        val retrievability = forgettingCurve(elapsedDays, card.stability)

        return when (rating) {
            Rating.AGAIN -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.AGAIN)
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
                val newStab = nextRecallStability(card.difficulty, card.stability, retrievability, rating)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = (nextInterval(newStab) * hardInterval).toInt().coerceAtMost(maximumInterval),
                    reps = card.reps + 1
                )
            }
            Rating.GOOD -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.GOOD)
                val newStab = nextRecallStability(card.difficulty, card.stability, retrievability, rating)
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
                val newStab = nextRecallStability(card.difficulty, card.stability, retrievability, rating)
                card.copy(
                    difficulty = newDifficulty,
                    stability = newStab,
                    state = CardState.REVIEW,
                    scheduledDays = (nextInterval(newStab) * easyBonus).toInt().coerceAtMost(maximumInterval),
                    reps = card.reps + 1
                )
            }
        }
    }

    private fun initStability(rating: Rating): Double {
        return when (rating) {
            Rating.AGAIN -> w[0]
            Rating.HARD -> w[1]
            Rating.GOOD -> w[2]
            Rating.EASY -> w[3]
        }
    }

    private fun initDifficulty(rating: Rating): Double {
        val difficulty = w[4] - (rating.ordinal) * w[5]
        return difficulty.coerceIn(1.0, 10.0)
    }

    private fun forgettingCurve(elapsedDays: Int, stability: Double): Double {
        return (1 + elapsedDays / (9 * stability)).pow(-1.0)
    }

    private fun nextInterval(stability: Double): Int {
        val newInterval = (stability / requestRetention.pow(1.0 / w[8]) *
                          (requestRetention.pow(1.0 / w[8]) - 1) * 9)
        return newInterval.toInt().coerceIn(1, maximumInterval)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val deltaDifficulty = rating.ordinal - 3 // -2, -1, 0, 1
        val newDifficulty = difficulty - w[6] * deltaDifficulty
        return meanReversion(w[4], newDifficulty).coerceIn(1.0, 10.0)
    }

    private fun meanReversion(init: Double, current: Double): Double {
        return w[7] * init + (1 - w[7]) * current
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0

        return stability * (
            1 + exp(w[8]) *
            (11 - difficulty) *
            stability.pow(-w[9]) *
            (exp((1 - retrievability) * w[10]) - 1) *
            hardPenalty *
            easyBonus
        )
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        return w[11] * difficulty.pow(-w[12]) *
               ((stability + 1).pow(w[13]) - 1) *
               exp((1 - retrievability) * w[14])
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0

        return stability * (
            exp(w[17]) *
            (11 - difficulty) *
            stability.pow(-w[18]) *
            (exp((1 - retrievability) * w[10]) - 1) *
            hardPenalty *
            easyBonus + 1
        )
    }
}
