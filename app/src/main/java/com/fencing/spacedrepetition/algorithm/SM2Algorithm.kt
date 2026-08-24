// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.algorithm

import com.fencing.spacedrepetition.util.Time
import kotlin.math.max

/**
 * SM-2 (SuperMemo 2) Algorithm Implementation
 * Classic spaced repetition algorithm
 */
class SM2Algorithm(
    private var maximumInterval: Int = 36500, // Default: 100 years
    private var intervalModifier: Double = 1.0 // 1.0 = 100 %, scales all review intervals
) {

    /**
     * Update the maximum interval setting
     */
    fun setMaximumInterval(days: Int) {
        maximumInterval = days.coerceAtLeast(1)
    }

    /**
     * Update the interval modifier.
     * @param modifierPercent Integer percentage, e.g. 100 for no change, 50 to halve
     *   intervals (more reviews), 200 to double them (fewer reviews).
     */
    fun setIntervalModifier(modifierPercent: Int) {
        intervalModifier = (modifierPercent / 100.0).coerceIn(0.1, 10.0)
    }

    data class SM2Card(
        val easeFactor: Double = 2.5,
        val interval: Int = 0,
        val repetitions: Int = 0,
        val lastReview: Long = 0L
    )

    enum class Quality {
        COMPLETE_BLACKOUT,  // 0 - complete blackout
        INCORRECT,          // 1 - incorrect response
        DIFFICULT_RECALL,   // 2 - correct response recalled with serious difficulty
        DIFFICULT,          // 3 - correct response after hesitation
        EASY,              // 4 - correct response after a hesitation
        PERFECT            // 5 - perfect response
    }

    data class SM2SchedulingInfo(
        val card: SM2Card,
        val nextReviewDate: Long
    )

    /**
     * Schedule a card review based on quality rating (0-5)
     */
    fun schedule(card: SM2Card, quality: Quality, now: Long = Time.now()): SM2SchedulingInfo {
        return schedule(card, quality.ordinal, now)
    }

    /**
     * Schedule a card review based on quality rating (0-5)
     * @param card Current card state
     * @param quality Quality of recall (0-5)
     * @param now Current timestamp
     * @return Updated card and next review date
     */
    fun schedule(card: SM2Card, quality: Int, now: Long = Time.now()): SM2SchedulingInfo {
        require(quality in 0..5) { "Quality must be between 0 and 5" }

        // Calculate new ease factor
        val newEaseFactor = calculateEaseFactor(card.easeFactor, quality)

        // If quality < 3, start over
        if (quality < 3) {
            val newCard = card.copy(
                easeFactor = newEaseFactor,
                interval = 1,
                repetitions = 0,
                lastReview = now
            )
            val nextReview = now + (1 * 24 * 60 * 60 * 1000L) // 1 day
            return SM2SchedulingInfo(newCard, nextReview)
        }

        // Calculate new interval, applying the interval modifier from the third review onwards
        val newInterval = when (card.repetitions) {
            0 -> 1
            1 -> 6
            else -> (card.interval * newEaseFactor * intervalModifier).toInt().coerceIn(1, maximumInterval)
        }

        val newCard = card.copy(
            easeFactor = newEaseFactor,
            interval = newInterval,
            repetitions = card.repetitions + 1,
            lastReview = now
        )

        val nextReview = now + (newInterval * 24 * 60 * 60 * 1000L)
        return SM2SchedulingInfo(newCard, nextReview)
    }

    /**
     * Convert simple 4-grade rating to SM-2's 6-grade scale
     */
    fun convertRating(rating: SimpleRating): Quality {
        return when (rating) {
            SimpleRating.AGAIN -> Quality.COMPLETE_BLACKOUT
            SimpleRating.HARD -> Quality.DIFFICULT
            SimpleRating.GOOD -> Quality.EASY
            SimpleRating.EASY -> Quality.PERFECT
        }
    }

    enum class SimpleRating {
        AGAIN, HARD, GOOD, EASY
    }

    /**
     * Calculate new ease factor based on quality of recall
     * EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
     */
    private fun calculateEaseFactor(currentEF: Double, quality: Int): Double {
        val newEF = currentEF + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
        return max(1.3, newEF) // Minimum ease factor is 1.3
    }

    /**
     * Get the recommended interval in days for a card
     */
    fun getIntervalDays(card: SM2Card): Int {
        return card.interval
    }

    /**
     * Check if a card is due for review
     */
    fun isDue(card: SM2Card, now: Long = Time.now()): Boolean {
        if (card.lastReview == 0L) return true
        val daysSinceReview = (now - card.lastReview) / (1000 * 60 * 60 * 24)
        return daysSinceReview >= card.interval
    }

    /**
     * Get all possible scheduling outcomes for a card
     */
    fun getSchedulingCards(card: SM2Card, now: Long = Time.now()): Map<Quality, SM2Card> {
        return Quality.values().associateWith { quality ->
            schedule(card, quality, now).card
        }
    }

    /**
     * Get scheduling info for simple 4-grade rating system
     */
    fun getSimpleSchedulingCards(card: SM2Card, now: Long = Time.now()): Map<SimpleRating, SM2Card> {
        return SimpleRating.values().associateWith { rating ->
            schedule(card, convertRating(rating), now).card
        }
    }
}
