// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.algorithm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class SM2AlgorithmTest {

    private lateinit var algorithm: SM2Algorithm
    private val testTimestamp = 1704067200000L // 2024-01-01 00:00:00
    private val oneDayMs = 24 * 60 * 60 * 1000L

    @Before
    fun setup() {
        algorithm = SM2Algorithm()
    }

    // Helper function to compare doubles with tolerance
    private fun assertDoubleEquals(expected: Double, actual: Double, delta: Double = 0.001) {
        assertTrue(
            "Expected $expected but got $actual (difference: ${abs(expected - actual)})",
            abs(expected - actual) < delta
        )
    }

    // ========== Initial Card State Tests ==========

    @Test
    fun testInitialCard_HasCorrectDefaults() {
        val card = SM2Algorithm.SM2Card()

        assertDoubleEquals(2.5, card.easeFactor)
        assertEquals(0, card.interval)
        assertEquals(0, card.repetitions)
        assertEquals(0L, card.lastReview)
    }

    // ========== Quality Rating Tests (0-5) ==========

    @Test
    fun testQuality0_CompleteBlackout_ResetsCard() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 10,
            repetitions = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.COMPLETE_BLACKOUT, testTimestamp + oneDayMs)

        assertEquals(1, result.card.interval)
        assertEquals(0, result.card.repetitions)
        assertTrue(result.card.easeFactor < 2.5) // EF should decrease
        assertTrue(result.card.easeFactor >= 1.3) // But not below minimum
    }

    @Test
    fun testQuality1_Incorrect_ResetsCard() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 10,
            repetitions = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.INCORRECT, testTimestamp + oneDayMs)

        assertEquals(1, result.card.interval)
        assertEquals(0, result.card.repetitions)
        assertTrue(result.card.easeFactor < 2.5)
        assertTrue(result.card.easeFactor >= 1.3)
    }

    @Test
    fun testQuality2_DifficultRecall_ResetsCard() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 10,
            repetitions = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.DIFFICULT_RECALL, testTimestamp + oneDayMs)

        assertEquals(1, result.card.interval)
        assertEquals(0, result.card.repetitions)
        assertTrue(result.card.easeFactor < 2.5)
        assertTrue(result.card.easeFactor >= 1.3)
    }

    @Test
    fun testQuality3_Difficult_FirstRepetition() {
        val card = SM2Algorithm.SM2Card()

        val result = algorithm.schedule(card, SM2Algorithm.Quality.DIFFICULT, testTimestamp)

        assertEquals(1, result.card.interval)
        assertEquals(1, result.card.repetitions)
    }

    @Test
    fun testQuality3_Difficult_SecondRepetition() {
        val card = SM2Algorithm.SM2Card(
            interval = 1,
            repetitions = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.DIFFICULT, testTimestamp + oneDayMs)

        assertEquals(6, result.card.interval)
        assertEquals(2, result.card.repetitions)
    }

    @Test
    fun testQuality4_Easy_FirstRepetition() {
        val card = SM2Algorithm.SM2Card()

        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp)

        assertEquals(1, result.card.interval)
        assertEquals(1, result.card.repetitions)
    }

    @Test
    fun testQuality5_Perfect_FirstRepetition() {
        val card = SM2Algorithm.SM2Card()

        val result = algorithm.schedule(card, SM2Algorithm.Quality.PERFECT, testTimestamp)

        assertEquals(1, result.card.interval)
        assertEquals(1, result.card.repetitions)
    }

    // ========== Interval Progression Tests ==========

    @Test
    fun testIntervalProgression_FirstRepetition() {
        val card = SM2Algorithm.SM2Card()

        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp)

        assertEquals(1, result.card.interval)
        assertEquals(1, result.card.repetitions)
    }

    @Test
    fun testIntervalProgression_SecondRepetition() {
        val card = SM2Algorithm.SM2Card(
            interval = 1,
            repetitions = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp + oneDayMs)

        assertEquals(6, result.card.interval)
        assertEquals(2, result.card.repetitions)
    }

    @Test
    fun testIntervalProgression_ThirdRepetition_UseEaseFactor() {
        val easeFactor = 2.5
        val card = SM2Algorithm.SM2Card(
            easeFactor = easeFactor,
            interval = 6,
            repetitions = 2,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp + (6 * oneDayMs))

        assertEquals((6 * easeFactor).toInt(), result.card.interval)
        assertEquals(3, result.card.repetitions)
    }

    @Test
    fun testIntervalProgression_FourthRepetition_UseEaseFactor() {
        val easeFactor = 2.5
        val card = SM2Algorithm.SM2Card(
            easeFactor = easeFactor,
            interval = 15, // (6 * 2.5).toInt()
            repetitions = 3,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp + (15 * oneDayMs))

        assertEquals((15 * easeFactor).toInt(), result.card.interval)
        assertEquals(4, result.card.repetitions)
    }

    // ========== Ease Factor Tests ==========

    @Test
    fun testEaseFactor_IncreasesWithPerfectRating() {
        val card = SM2Algorithm.SM2Card(easeFactor = 2.5)

        val result = algorithm.schedule(card, SM2Algorithm.Quality.PERFECT, testTimestamp)

        assertTrue(result.card.easeFactor > 2.5)
    }

    @Test
    fun testEaseFactor_UnchangedWithEasyRating() {
        // Quality.EASY = ordinal 4; formula: EF + (0.1 - (5-4)*(0.08+(5-4)*0.02)) = EF + 0 = EF
        val card = SM2Algorithm.SM2Card(easeFactor = 2.5)

        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp)

        assertDoubleEquals(2.5, result.card.easeFactor)
    }

    @Test
    fun testEaseFactor_SlightlyDecreasesWithDifficultRating() {
        val card = SM2Algorithm.SM2Card(easeFactor = 2.5)

        val result = algorithm.schedule(card, SM2Algorithm.Quality.DIFFICULT, testTimestamp)

        assertTrue(result.card.easeFactor < 2.5)
        assertTrue(result.card.easeFactor >= 1.3) // Above minimum
    }

    @Test
    fun testEaseFactor_DecreasesWithBadRatings() {
        val card = SM2Algorithm.SM2Card(easeFactor = 2.5)

        val result0 = algorithm.schedule(card, SM2Algorithm.Quality.COMPLETE_BLACKOUT, testTimestamp)
        val result1 = algorithm.schedule(card, SM2Algorithm.Quality.INCORRECT, testTimestamp)
        val result2 = algorithm.schedule(card, SM2Algorithm.Quality.DIFFICULT_RECALL, testTimestamp)

        assertTrue(result0.card.easeFactor < 2.5)
        assertTrue(result1.card.easeFactor < 2.5)
        assertTrue(result2.card.easeFactor < 2.5)
    }

    @Test
    fun testEaseFactor_HasMinimumBound() {
        var card = SM2Algorithm.SM2Card(easeFactor = 1.5)

        // Apply many poor ratings to try to push below minimum
        repeat(20) {
            card = algorithm.schedule(card, SM2Algorithm.Quality.COMPLETE_BLACKOUT, testTimestamp + (it * oneDayMs)).card
        }

        assertTrue(card.easeFactor >= 1.3)
    }

    @Test
    fun testEaseFactor_Formula_Quality0() {
        val initialEF = 2.5
        val card = SM2Algorithm.SM2Card(easeFactor = initialEF)

        val result = algorithm.schedule(card, 0, testTimestamp)

        // EF' = EF + (0.1 - (5 - 0) * (0.08 + (5 - 0) * 0.02))
        // EF' = 2.5 + (0.1 - 5 * (0.08 + 5 * 0.02))
        // EF' = 2.5 + (0.1 - 5 * 0.18)
        // EF' = 2.5 + (0.1 - 0.9) = 2.5 - 0.8 = 1.7
        assertDoubleEquals(1.7, result.card.easeFactor, 0.01)
    }

    @Test
    fun testEaseFactor_Formula_Quality3() {
        val initialEF = 2.5
        val card = SM2Algorithm.SM2Card(easeFactor = initialEF)

        val result = algorithm.schedule(card, 3, testTimestamp)

        // EF' = EF + (0.1 - (5 - 3) * (0.08 + (5 - 3) * 0.02))
        // EF' = 2.5 + (0.1 - 2 * (0.08 + 2 * 0.02))
        // EF' = 2.5 + (0.1 - 2 * 0.12)
        // EF' = 2.5 + (0.1 - 0.24) = 2.5 - 0.14 = 2.36
        assertDoubleEquals(2.36, result.card.easeFactor, 0.01)
    }

    @Test
    fun testEaseFactor_Formula_Quality5() {
        val initialEF = 2.5
        val card = SM2Algorithm.SM2Card(easeFactor = initialEF)

        val result = algorithm.schedule(card, 5, testTimestamp)

        // EF' = EF + (0.1 - (5 - 5) * (0.08 + (5 - 5) * 0.02))
        // EF' = 2.5 + (0.1 - 0) = 2.6
        assertDoubleEquals(2.6, result.card.easeFactor, 0.01)
    }

    // ========== Simple Rating Conversion Tests ==========

    @Test
    fun testConvertRating_Again_ToCompleteBlackout() {
        val quality = algorithm.convertRating(SM2Algorithm.SimpleRating.AGAIN)
        assertEquals(SM2Algorithm.Quality.COMPLETE_BLACKOUT, quality)
    }

    @Test
    fun testConvertRating_Hard_ToDifficult() {
        val quality = algorithm.convertRating(SM2Algorithm.SimpleRating.HARD)
        assertEquals(SM2Algorithm.Quality.DIFFICULT, quality)
    }

    @Test
    fun testConvertRating_Good_ToEasy() {
        val quality = algorithm.convertRating(SM2Algorithm.SimpleRating.GOOD)
        assertEquals(SM2Algorithm.Quality.EASY, quality)
    }

    @Test
    fun testConvertRating_Easy_ToPerfect() {
        val quality = algorithm.convertRating(SM2Algorithm.SimpleRating.EASY)
        assertEquals(SM2Algorithm.Quality.PERFECT, quality)
    }

    // ========== isDue Tests ==========

    @Test
    fun testIsDue_NewCard_IsAlwaysDue() {
        val card = SM2Algorithm.SM2Card()
        assertTrue(algorithm.isDue(card, testTimestamp))
    }

    @Test
    fun testIsDue_CardNotDueYet_ReturnsFalse() {
        val card = SM2Algorithm.SM2Card(
            interval = 5,
            lastReview = testTimestamp
        )

        val currentTime = testTimestamp + (3 * oneDayMs) // Only 3 days passed, need 5
        assertFalse(algorithm.isDue(card, currentTime))
    }

    @Test
    fun testIsDue_CardExactlyDue_ReturnsTrue() {
        val card = SM2Algorithm.SM2Card(
            interval = 5,
            lastReview = testTimestamp
        )

        val currentTime = testTimestamp + (5 * oneDayMs)
        assertTrue(algorithm.isDue(card, currentTime))
    }

    @Test
    fun testIsDue_CardOverdue_ReturnsTrue() {
        val card = SM2Algorithm.SM2Card(
            interval = 5,
            lastReview = testTimestamp
        )

        val currentTime = testTimestamp + (10 * oneDayMs)
        assertTrue(algorithm.isDue(card, currentTime))
    }

    // ========== getIntervalDays Tests ==========

    @Test
    fun testGetIntervalDays_ReturnsCorrectInterval() {
        val card = SM2Algorithm.SM2Card(interval = 15)
        assertEquals(15, algorithm.getIntervalDays(card))
    }

    @Test
    fun testGetIntervalDays_NewCard_ReturnsZero() {
        val card = SM2Algorithm.SM2Card()
        assertEquals(0, algorithm.getIntervalDays(card))
    }

    // ========== getSchedulingCards Tests ==========

    @Test
    fun testGetSchedulingCards_ReturnsAllQualities() {
        val card = SM2Algorithm.SM2Card()
        val schedulingCards = algorithm.getSchedulingCards(card, testTimestamp)

        assertEquals(6, schedulingCards.size)
        assertTrue(schedulingCards.containsKey(SM2Algorithm.Quality.COMPLETE_BLACKOUT))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.Quality.INCORRECT))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.Quality.DIFFICULT_RECALL))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.Quality.DIFFICULT))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.Quality.EASY))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.Quality.PERFECT))
    }

    @Test
    fun testGetSchedulingCards_ShowsCorrectIntervals() {
        val card = SM2Algorithm.SM2Card()
        val schedulingCards = algorithm.getSchedulingCards(card, testTimestamp)

        // All should give interval of 1 for first review
        assertEquals(1, schedulingCards[SM2Algorithm.Quality.DIFFICULT]?.interval)
        assertEquals(1, schedulingCards[SM2Algorithm.Quality.EASY]?.interval)
        assertEquals(1, schedulingCards[SM2Algorithm.Quality.PERFECT]?.interval)
    }

    @Test
    fun testGetSchedulingCards_ShowsCorrectRepetitions() {
        val card = SM2Algorithm.SM2Card()
        val schedulingCards = algorithm.getSchedulingCards(card, testTimestamp)

        // Passing grades increment repetitions
        assertEquals(1, schedulingCards[SM2Algorithm.Quality.DIFFICULT]?.repetitions)
        assertEquals(1, schedulingCards[SM2Algorithm.Quality.EASY]?.repetitions)
        assertEquals(1, schedulingCards[SM2Algorithm.Quality.PERFECT]?.repetitions)

        // Failing grades reset repetitions
        assertEquals(0, schedulingCards[SM2Algorithm.Quality.COMPLETE_BLACKOUT]?.repetitions)
        assertEquals(0, schedulingCards[SM2Algorithm.Quality.INCORRECT]?.repetitions)
        assertEquals(0, schedulingCards[SM2Algorithm.Quality.DIFFICULT_RECALL]?.repetitions)
    }

    @Test
    fun testGetSimpleSchedulingCards_ReturnsFourRatings() {
        val card = SM2Algorithm.SM2Card()
        val schedulingCards = algorithm.getSimpleSchedulingCards(card, testTimestamp)

        assertEquals(4, schedulingCards.size)
        assertTrue(schedulingCards.containsKey(SM2Algorithm.SimpleRating.AGAIN))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.SimpleRating.HARD))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.SimpleRating.GOOD))
        assertTrue(schedulingCards.containsKey(SM2Algorithm.SimpleRating.EASY))
    }

    // ========== Next Review Date Tests ==========

    @Test
    fun testNextReviewDate_FirstReview_OneDayLater() {
        val card = SM2Algorithm.SM2Card()
        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp)

        val expectedNextReview = testTimestamp + oneDayMs
        assertEquals(expectedNextReview, result.nextReviewDate)
    }

    @Test
    fun testNextReviewDate_SecondReview_SixDaysLater() {
        val card = SM2Algorithm.SM2Card(
            interval = 1,
            repetitions = 1,
            lastReview = testTimestamp
        )
        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp + oneDayMs)

        val expectedNextReview = testTimestamp + oneDayMs + (6 * oneDayMs)
        assertEquals(expectedNextReview, result.nextReviewDate)
    }

    @Test
    fun testNextReviewDate_CalculatedCorrectly() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 10,
            repetitions = 3,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (10 * oneDayMs)
        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)

        val expectedInterval = (10 * 2.5).toInt() // Quality.EASY (q=4) leaves EF unchanged at 2.5
        val expectedNextReview = reviewTime + (expectedInterval * oneDayMs)
        assertEquals(expectedNextReview, result.nextReviewDate)
    }

    // ========== Schedule Method Validation Tests ==========

    @Test
    fun testSchedule_InvalidQuality_Negative_ThrowsException() {
        val card = SM2Algorithm.SM2Card()
        assertThrows(IllegalArgumentException::class.java) {
            algorithm.schedule(card, -1, testTimestamp)
        }
    }

    @Test
    fun testSchedule_InvalidQuality_TooHigh_ThrowsException() {
        val card = SM2Algorithm.SM2Card()
        assertThrows(IllegalArgumentException::class.java) {
            algorithm.schedule(card, 6, testTimestamp)
        }
    }

    @Test
    fun testSchedule_ValidQuality_0_DoesNotThrow() {
        val card = SM2Algorithm.SM2Card()
        algorithm.schedule(card, 0, testTimestamp) // Should not throw
    }

    @Test
    fun testSchedule_ValidQuality_5_DoesNotThrow() {
        val card = SM2Algorithm.SM2Card()
        algorithm.schedule(card, 5, testTimestamp) // Should not throw
    }

    // ========== LastReview Update Tests ==========

    @Test
    fun testLastReview_UpdatedCorrectly() {
        val card = SM2Algorithm.SM2Card()
        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp)

        assertEquals(testTimestamp, result.card.lastReview)
    }

    @Test
    fun testLastReview_UpdatedOnSubsequentReviews() {
        val card = SM2Algorithm.SM2Card(
            interval = 1,
            repetitions = 1,
            lastReview = testTimestamp
        )

        val newReviewTime = testTimestamp + (5 * oneDayMs)
        val result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, newReviewTime)

        assertEquals(newReviewTime, result.card.lastReview)
    }

    // ========== setIntervalModifier Tests ==========

    @Test
    fun testSetIntervalModifier_100Percent_SameAsDefault() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 6,
            repetitions = 2,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (6 * oneDayMs)

        val defaultAlgo = SM2Algorithm()
        val explicit100Algo = SM2Algorithm()
        explicit100Algo.setIntervalModifier(100)

        val defaultResult = defaultAlgo.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)
        val explicit100Result = explicit100Algo.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)

        assertEquals(defaultResult.card.interval, explicit100Result.card.interval)
    }

    @Test
    fun testSetIntervalModifier_50Percent_HalvesThirdPlusIntervals() {
        // Third repetition: default interval = (6 * 2.5 * 1.0).toInt() = 15
        // With 50 % modifier:                (6 * 2.5 * 0.5).toInt() = 7
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 6,
            repetitions = 2,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (6 * oneDayMs)

        val modifiedAlgo = SM2Algorithm()
        modifiedAlgo.setIntervalModifier(50)

        val modifiedResult = modifiedAlgo.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)

        // Quality.EASY (q=4) leaves EF unchanged at 2.5
        val expectedInterval = (6 * 2.5 * 0.5).toInt()
        assertEquals(expectedInterval, modifiedResult.card.interval)
        // Sanity: shorter than default (15)
        assertTrue(modifiedResult.card.interval < 15)
    }

    @Test
    fun testSetIntervalModifier_200Percent_DoublesThirdPlusIntervals() {
        // Third repetition: default = 15; with 200 % modifier = (6 * 2.5 * 2.0).toInt() = 30
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 6,
            repetitions = 2,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (6 * oneDayMs)

        val modifiedAlgo = SM2Algorithm()
        modifiedAlgo.setIntervalModifier(200)

        val modifiedResult = modifiedAlgo.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)

        val expectedInterval = (6 * 2.5 * 2.0).toInt()
        assertEquals(expectedInterval, modifiedResult.card.interval)
        assertTrue(modifiedResult.card.interval > 15)
    }

    @Test
    fun testSetIntervalModifier_NotApplied_ToFirstRepetition() {
        // First repetition (rep=0): interval is always 1, modifier must not change this
        val newCard = SM2Algorithm.SM2Card()
        val modifiedAlgo = SM2Algorithm()
        modifiedAlgo.setIntervalModifier(50)

        val result = modifiedAlgo.schedule(newCard, SM2Algorithm.Quality.EASY, testTimestamp)
        assertEquals(1, result.card.interval)
    }

    @Test
    fun testSetIntervalModifier_NotApplied_ToSecondRepetition() {
        // Second repetition (rep=1): interval is always 6, modifier must not change this
        val card = SM2Algorithm.SM2Card(
            interval = 1,
            repetitions = 1,
            lastReview = testTimestamp
        )
        val modifiedAlgo = SM2Algorithm()
        modifiedAlgo.setIntervalModifier(50)

        val result = modifiedAlgo.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp + oneDayMs)
        assertEquals(6, result.card.interval)
    }

    @Test
    fun testSetIntervalModifier_LowerValue_ShorterIntervals() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 10,
            repetitions = 3,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (10 * oneDayMs)

        val highModifierAlgo = SM2Algorithm()
        highModifierAlgo.setIntervalModifier(150)
        val lowModifierAlgo = SM2Algorithm()
        lowModifierAlgo.setIntervalModifier(50)

        val highResult = highModifierAlgo.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)
        val lowResult = lowModifierAlgo.schedule(card, SM2Algorithm.Quality.EASY, reviewTime)

        assertTrue(
            "Lower modifier should produce shorter interval: low=${lowResult.card.interval} high=${highResult.card.interval}",
            lowResult.card.interval < highResult.card.interval
        )
    }

    @Test
    fun testSetIntervalModifier_ClampsLowValues_IntervalAtLeastOne() {
        val card = SM2Algorithm.SM2Card(
            easeFactor = 1.3,
            interval = 1,
            repetitions = 2,
            lastReview = testTimestamp
        )
        val algo = SM2Algorithm()
        algo.setIntervalModifier(0) // clamped to 0.1 internally

        val result = algo.schedule(card, SM2Algorithm.Quality.DIFFICULT, testTimestamp + oneDayMs)
        assertTrue(result.card.interval >= 1)
    }

    @Test
    fun testSetIntervalModifier_ClampsHighValues_IntervalDoesNotExceedMaximum() {
        val algo = SM2Algorithm()
        algo.setIntervalModifier(2000) // clamped to 10.0

        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 10000,
            repetitions = 5,
            lastReview = testTimestamp
        )
        val result = algo.schedule(card, SM2Algorithm.Quality.EASY, testTimestamp + (10000 * oneDayMs))
        assertTrue(result.card.interval <= 36500)
    }

    @Test
    fun testSetIntervalModifier_ResetsDoNotApplyModifier() {
        // Failing ratings reset the card; the reset interval (1) should not be scaled
        val card = SM2Algorithm.SM2Card(
            easeFactor = 2.5,
            interval = 15,
            repetitions = 3,
            lastReview = testTimestamp
        )
        val algo = SM2Algorithm()
        algo.setIntervalModifier(50)

        val result = algo.schedule(card, SM2Algorithm.Quality.COMPLETE_BLACKOUT, testTimestamp + (15 * oneDayMs))
        assertEquals(1, result.card.interval)
        assertEquals(0, result.card.repetitions)
    }

    // ========== Realistic Scenario Tests ==========

    @Test
    fun testRealisticScenario_LearningProgression() {
        var card = SM2Algorithm.SM2Card()
        var currentTime = testTimestamp

        // First review: interval = 1
        var result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime)
        card = result.card
        assertEquals(1, card.interval)
        assertEquals(1, card.repetitions)

        // Second review: interval = 6
        currentTime += (card.interval * oneDayMs)
        result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime)
        card = result.card
        assertEquals(6, card.interval)
        assertEquals(2, card.repetitions)

        // Third review: interval = 6 * EF
        currentTime += (card.interval * oneDayMs)
        result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime)
        card = result.card
        assertTrue(card.interval > 6)
        assertEquals(3, card.repetitions)

        // Fourth review: interval should continue growing
        val previousInterval = card.interval
        currentTime += (card.interval * oneDayMs)
        result = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime)
        card = result.card
        assertTrue(card.interval > previousInterval)
        assertEquals(4, card.repetitions)
    }

    @Test
    fun testRealisticScenario_FailureAndRecovery() {
        var card = SM2Algorithm.SM2Card()
        var currentTime = testTimestamp

        // Learn the card successfully
        card = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime).card
        currentTime += (card.interval * oneDayMs)
        card = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime).card
        currentTime += (card.interval * oneDayMs)
        card = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime).card

        val easeFactor = card.easeFactor
        assertTrue(card.repetitions >= 3)

        // Fail the card
        currentTime += (card.interval * oneDayMs)
        card = algorithm.schedule(card, SM2Algorithm.Quality.INCORRECT, currentTime).card

        // Card should be reset
        assertEquals(1, card.interval)
        assertEquals(0, card.repetitions)
        assertTrue(card.easeFactor < easeFactor) // EF decreased

        // Learn it again
        currentTime += (card.interval * oneDayMs)
        card = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime).card
        assertEquals(1, card.interval)
        assertEquals(1, card.repetitions)
    }

    @Test
    fun testRealisticScenario_ConsistentPerfectRatings() {
        var card = SM2Algorithm.SM2Card()
        var currentTime = testTimestamp

        // Review with perfect ratings
        repeat(10) { iteration ->
            card = algorithm.schedule(card, SM2Algorithm.Quality.PERFECT, currentTime).card
            currentTime += (card.interval * oneDayMs)

            // Ease factor should keep increasing (up to a point)
            if (iteration > 0) {
                assertTrue(card.easeFactor >= 2.5)
            }
        }

        // After many perfect reviews, intervals should be quite long
        assertTrue(card.interval > 50)
    }

    @Test
    fun testRealisticScenario_MixedRatings() {
        var card = SM2Algorithm.SM2Card()
        var currentTime = testTimestamp

        val ratings = listOf(
            SM2Algorithm.Quality.EASY,
            SM2Algorithm.Quality.PERFECT,
            SM2Algorithm.Quality.DIFFICULT,
            SM2Algorithm.Quality.EASY,
            SM2Algorithm.Quality.EASY,
            SM2Algorithm.Quality.DIFFICULT,
            SM2Algorithm.Quality.PERFECT
        )

        ratings.forEach { quality ->
            card = algorithm.schedule(card, quality, currentTime).card
            currentTime += (card.interval * oneDayMs)
        }

        // Card should have progressed despite mixed ratings
        assertTrue(card.repetitions >= 7)
        assertTrue(card.interval > 1)
    }

    @Test
    fun testRealisticScenario_MultipleFailures() {
        var card = SM2Algorithm.SM2Card()
        var currentTime = testTimestamp

        // Learn card
        card = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime).card
        currentTime += (card.interval * oneDayMs)
        card = algorithm.schedule(card, SM2Algorithm.Quality.EASY, currentTime).card
        currentTime += (card.interval * oneDayMs)

        // Fail multiple times
        repeat(3) {
            card = algorithm.schedule(card, SM2Algorithm.Quality.COMPLETE_BLACKOUT, currentTime).card
            currentTime += (card.interval * oneDayMs)
        }

        // Ease factor should be at minimum
        assertDoubleEquals(1.3, card.easeFactor, 0.1)
        assertEquals(0, card.repetitions) // Should be reset
    }
}
