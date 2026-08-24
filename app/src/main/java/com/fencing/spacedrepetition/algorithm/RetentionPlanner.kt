// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.algorithm

import com.fencing.spacedrepetition.data.model.PracticeHistoryStats
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Real-world practice cadence, either sampled from review history or entered by the user.
 *
 * @property daysPerWeek Average number of days per week with at least one practice.
 * @property setsPerPractice Average number of card reviews (sets) per practice day.
 */
data class ScheduleEstimate(
    val daysPerWeek: Double,
    val setsPerPractice: Double
)

/**
 * Derives a desired-retention value that fits the user's practice schedule.
 *
 * The model: a practice schedule of `daysPerWeek × setsPerPractice` reviews per week can
 * sustain each of the N cards in rotation being reviewed every `7·N / capacity` days. The
 * FSRS-6 forgetting curve is then inverted to find the retention target whose review
 * interval matches that pace for a typical mature card.
 */
object RetentionPlanner {
    /** FSRS-6 default forgetting-curve decay (w20). */
    private const val DECAY = 0.1542

    /** Curve factor chosen so that R(S, S) = 90 %, as in the FSRS-6 reference implementation. */
    private val FACTOR = 0.9.pow(-1.0 / DECAY) - 1.0

    /** Representative stability of a settled card, in days, used to anchor the inversion. */
    private const val TYPICAL_STABILITY_DAYS = 30.0

    /** Default number of days of review history sampled when estimating the user's schedule. */
    const val DEFAULT_HISTORY_WINDOW_DAYS = 56

    /** Valid range for the user-tunable history fit window, in days. */
    const val MIN_HISTORY_WINDOW_DAYS = 7
    const val MAX_HISTORY_WINDOW_DAYS = 365

    /** Minimum distinct practice days before a history estimate is considered meaningful. */
    private const val MIN_PRACTICE_DAYS_FOR_ESTIMATE = 3

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Suggested desired retention (integer percent, clamped to the app's valid range) for a
     * schedule of [daysPerWeek] practices × [setsPerPractice] reviews with [cardsInRotation]
     * cards to maintain.
     */
    fun suggestedRetention(daysPerWeek: Double, setsPerPractice: Double, cardsInRotation: Int): Int {
        val weeklyCapacity = max(daysPerWeek * setsPerPractice, 1.0)
        val targetIntervalDays = 7.0 * max(cardsInRotation, 1) / weeklyCapacity
        val retention = (1.0 + FACTOR * targetIntervalDays / TYPICAL_STABILITY_DAYS).pow(-DECAY)
        return (retention * 100).roundToInt()
            .coerceIn(ThemePreferences.MIN_FSRS_RETENTION, ThemePreferences.MAX_FSRS_RETENTION)
    }

    /**
     * Estimates the user's actual practice cadence from review-log aggregates, or null when
     * there is too little history to say anything useful.
     *
     * @param stats Aggregates for reviews at or after [windowStartMillis].
     * @param nowMillis Current time; the observation span runs from the later of
     *   [windowStartMillis] and the first logged review up to now (at least one week).
     */
    fun estimateSchedule(
        stats: PracticeHistoryStats?,
        nowMillis: Long,
        windowStartMillis: Long
    ): ScheduleEstimate? {
        if (stats == null || stats.practiceDays < MIN_PRACTICE_DAYS_FOR_ESTIMATE) return null
        val spanStart = max(windowStartMillis, stats.firstReviewTime)
        val spanDays = max((nowMillis - spanStart).toDouble() / DAY_MS, 7.0)
        return ScheduleEstimate(
            daysPerWeek = (stats.practiceDays * 7.0 / spanDays).coerceIn(0.5, 7.0),
            setsPerPractice = stats.totalReviews.toDouble() / stats.practiceDays
        )
    }
}
