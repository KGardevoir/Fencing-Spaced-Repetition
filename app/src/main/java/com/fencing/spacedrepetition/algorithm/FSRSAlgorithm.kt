package com.fencing.spacedrepetition.algorithm

import kotlin.math.exp
import kotlin.math.pow

/**
 * FSRS (Free Spaced Repetition Scheduler) Algorithm Implementation
 * Based on the FSRS-5 specification
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

    // FSRS-5 Parameters (optimized defaults)
    private val w = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949,
        0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
        1.01925, 1.9395, 0.11, 0.29605, 2.2698,
        0.2315, 2.9898, 0.51655, 0.6621
    )

    private val requestRetention = 0.9 // Target retention rate
    private val DECAY = -0.5
    private val FACTOR = 19.0 / 81.0

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

    private fun scheduleLearning(card: FSRSCard, rating: Rating): FSRSCard {
        return when (rating) {
            Rating.AGAIN -> {
                val newDifficulty = nextDifficulty(card.difficulty, Rating.AGAIN)
                val newStab = shortTermStability(card.stability, rating)
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
                val newStab = shortTermStability(card.stability, rating)
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
                val newStab = shortTermStability(card.stability, rating)
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
                val newStab = shortTermStability(card.stability, rating)
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
                    scheduledDays = nextInterval(newStab),
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
                    scheduledDays = nextInterval(newStab),
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
        val g = rating.ordinal + 1  // 1-indexed grade (AGAIN=1, HARD=2, GOOD=3, EASY=4)
        return (w[4] - exp(w[5] * (g - 1)) + 1).coerceIn(1.0, 10.0)
    }

    private fun forgettingCurve(elapsedDays: Int, stability: Double): Double {
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)
    }

    private fun nextInterval(stability: Double): Int {
        val newInterval = stability / FACTOR * (requestRetention.pow(1.0 / DECAY) - 1)
        return newInterval.toInt().coerceIn(1, maximumInterval)
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

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        return stability * exp(w[17] * (rating.ordinal + 1 - 3 + w[18]))
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        return w[11] * difficulty.pow(-w[12]) *
               ((stability + 1).pow(w[13]) - 1) *
               exp((1 - retrievability) * w[14])
    }
}
