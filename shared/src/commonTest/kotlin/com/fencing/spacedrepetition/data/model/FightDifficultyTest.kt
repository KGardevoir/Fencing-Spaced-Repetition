// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The 1-5 fight rating and what it is worth.
 *
 * The numbers are the feature, not an implementation detail: a rating is stored
 * on every review log and read back on an import, so changing what a 4 means
 * would silently re-price every fight already recorded. They are written out
 * here so that cannot happen quietly.
 */
class FightDifficultyTest {

    @Test
    fun `each rating maps to its multiplier`() {
        assertEquals(0.5, FightDifficulty.multiplierFor(1))
        assertEquals(0.75, FightDifficulty.multiplierFor(2))
        assertEquals(1.0, FightDifficulty.multiplierFor(3))
        assertEquals(1.25, FightDifficulty.multiplierFor(4))
        assertEquals(1.5, FightDifficulty.multiplierFor(5))
    }

    @Test
    fun `an unrated fight is neutral`() {
        assertEquals(1.0, FightDifficulty.multiplierFor(null))
        assertNull(FightDifficulty.fromRating(null))
    }

    @Test
    fun `a rating outside 1-5 is neutral rather than an error`() {
        // Ratings come back from the database and from imported files, where a
        // 0 or a 9 is possible however it got there. Treating it as neutral
        // keeps a strange row schedulable instead of throwing mid-review.
        listOf(0, -1, 6, 42).forEach { rating ->
            assertNull(FightDifficulty.fromRating(rating), "rating $rating")
            assertEquals(1.0, FightDifficulty.multiplierFor(rating), "rating $rating")
        }
    }

    @Test
    fun `rating 3 is the neutral one`() {
        assertEquals(3, FightDifficulty.NEUTRAL.rating)
        assertEquals(1.0, FightDifficulty.NEUTRAL.multiplier)
    }

    @Test
    fun `fromRating finds the entry for every rating it declares`() {
        FightDifficulty.entries.forEach { difficulty ->
            assertEquals(difficulty, FightDifficulty.fromRating(difficulty.rating))
        }
        assertEquals(listOf(1, 2, 3, 4, 5), FightDifficulty.entries.map { it.rating })
    }

    @Test
    fun `the fight multiplies with the opponent's skill rather than replacing it`() {
        // A 1.25 opponent in a rating-5 fight: 1.25 x 1.5.
        assertEquals(1.875, FightDifficulty.combinedMultiplier(1.25, 5))
        // An easy fight against the same opponent pulls back below neutral.
        assertEquals(0.625, FightDifficulty.combinedMultiplier(1.25, 1))
    }

    @Test
    fun `either half alone is the whole multiplier`() {
        // No opponent, hard fight.
        assertEquals(1.25, FightDifficulty.combinedMultiplier(1.0, 4))
        // Known opponent, unrated fight -- what every review recorded before
        // fight difficulty existed is worth.
        assertEquals(1.3, FightDifficulty.combinedMultiplier(1.3, null))
        // Neither.
        assertEquals(1.0, FightDifficulty.combinedMultiplier(1.0, null))
    }
}
