// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * How hard the fight itself was, on a 1-5 scale.
 *
 * The companion piece to [Opponent.skillMultiplier], and treated exactly the
 * same way: it scales the FSRS stability gain of a successful review. Where the
 * opponent multiplier says how strong the person across from you is, this says
 * how demanding the bout turned out to be -- tempo, fatigue, the pressure of a
 * pool bout versus a relaxed drill -- which the opponent's rating alone does
 * not capture.
 *
 * The two multiply: a 1.25 opponent in a rating-5 fight earns 1.25 x 1.5 =
 * 1.875 of the neutral stability gain. A rating of 3 is neutral, so leaving the
 * fight unrated changes nothing.
 */
enum class FightDifficulty(val rating: Int, val multiplier: Double, val label: String) {
    WALKOVER(1, 0.5, "Walkover"),
    COMFORTABLE(2, 0.75, "Comfortable"),
    EVEN(3, 1.0, "Even"),
    TESTING(4, 1.25, "Testing"),
    BRUTAL(5, 1.5, "Brutal");

    companion object {
        /**
         * The rating recorded when nothing else is known; equivalent to leaving
         * it unset.
         */
        val NEUTRAL = EVEN

        /** The difficulty for a stored rating, or null if [rating] is null or out of range. */
        fun fromRating(rating: Int?): FightDifficulty? = entries.find { it.rating == rating }

        /**
         * The stability multiplier for a stored rating. An unrated fight (null) or
         * a rating outside 1-5 is neutral, so it neither helps nor hurts.
         */
        fun multiplierFor(rating: Int?): Double = fromRating(rating)?.multiplier ?: 1.0

        /**
         * What a review actually earns: the opponent's skill multiplier and the
         * fight's own difficulty, multiplied.
         *
         * The one place the two are combined, so the rule is stated once and can
         * be checked on its own.
         */
        fun combinedMultiplier(skillMultiplier: Double, rating: Int?): Double =
            skillMultiplier * multiplierFor(rating)
    }
}
