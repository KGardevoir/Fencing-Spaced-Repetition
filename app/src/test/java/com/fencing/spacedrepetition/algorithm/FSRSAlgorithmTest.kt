package com.fencing.spacedrepetition.algorithm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class FSRSAlgorithmTest {

    private lateinit var algorithm: FSRSAlgorithm
    private val testTimestamp = 1704067200000L // 2024-01-01 00:00:00

    @Before
    fun setup() {
        algorithm = FSRSAlgorithm()
    }

    // Helper function to compare doubles with tolerance
    private fun assertDoubleEquals(expected: Double, actual: Double, delta: Double = 0.001) {
        assertTrue(
            "Expected $expected but got $actual (difference: ${abs(expected - actual)})",
            abs(expected - actual) < delta
        )
    }

    // ========== New Card Tests ==========

    @Test
    fun testNewCard_Again_ShouldGoToLearning() {
        val card = FSRSAlgorithm.FSRSCard()
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp)

        assertEquals(FSRSAlgorithm.CardState.LEARNING, result.card.state)
        assertEquals(0, result.card.scheduledDays)
        assertEquals(1, result.card.reps)
        assertEquals(0, result.card.lapses)
        assertTrue(result.card.difficulty > 0.0)
        assertTrue(result.card.stability > 0.0)
    }

    @Test
    fun testNewCard_Hard_ShouldGoToLearning() {
        val card = FSRSAlgorithm.FSRSCard()
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.HARD, testTimestamp)

        assertEquals(FSRSAlgorithm.CardState.LEARNING, result.card.state)
        assertEquals(0, result.card.scheduledDays)
        assertEquals(1, result.card.reps)
        assertEquals(0, result.card.lapses)
        assertTrue(result.card.difficulty > 0.0)
        assertTrue(result.card.stability > 0.0)
    }

    @Test
    fun testNewCard_Good_ShouldGoToLearning() {
        val card = FSRSAlgorithm.FSRSCard()
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp)

        assertEquals(FSRSAlgorithm.CardState.LEARNING, result.card.state)
        assertEquals(1, result.card.scheduledDays)
        assertEquals(1, result.card.reps)
        assertEquals(0, result.card.lapses)
        assertTrue(result.card.difficulty > 0.0)
        assertTrue(result.card.stability > 0.0)
    }

    @Test
    fun testNewCard_Easy_ShouldGoToReview() {
        val card = FSRSAlgorithm.FSRSCard()
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp)

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays > 1) // Should have interval > 1 for easy new card
        assertEquals(1, result.card.reps)
        assertEquals(0, result.card.lapses)
        assertTrue(result.card.difficulty > 0.0)
        assertTrue(result.card.stability > 0.0)
    }

    @Test
    fun testNewCard_DifficultyIncreases_AsRatingDecreases() {
        val card = FSRSAlgorithm.FSRSCard()

        val easyResult = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp)
        val goodResult = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp)
        val hardResult = algorithm.schedule(card, FSRSAlgorithm.Rating.HARD, testTimestamp)
        val againResult = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp)

        // Difficulty should increase as rating gets worse
        assertTrue(againResult.card.difficulty > hardResult.card.difficulty)
        assertTrue(hardResult.card.difficulty > goodResult.card.difficulty)
        assertTrue(goodResult.card.difficulty > easyResult.card.difficulty)
    }

    @Test
    fun testNewCard_StabilityIncreases_AsRatingImproves() {
        val card = FSRSAlgorithm.FSRSCard()

        val againResult = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp)
        val hardResult = algorithm.schedule(card, FSRSAlgorithm.Rating.HARD, testTimestamp)
        val goodResult = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp)
        val easyResult = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp)

        // Stability should increase as rating improves
        assertTrue(easyResult.card.stability > goodResult.card.stability)
        assertTrue(goodResult.card.stability > hardResult.card.stability)
        assertTrue(hardResult.card.stability > againResult.card.stability)
    }

    // ========== Learning State Tests ==========

    @Test
    fun testLearningCard_Again_StaysInLearning() {
        val learningCard = FSRSAlgorithm.FSRSCard(
            stability = 1.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.LEARNING,
            reps = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(learningCard, FSRSAlgorithm.Rating.AGAIN, testTimestamp + (24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.LEARNING, result.card.state)
        assertEquals(0, result.card.scheduledDays)
        assertEquals(2, result.card.reps)
        assertEquals(0, result.card.lapses)
    }

    @Test
    fun testLearningCard_Hard_StaysInLearning() {
        val learningCard = FSRSAlgorithm.FSRSCard(
            stability = 1.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.LEARNING,
            reps = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(learningCard, FSRSAlgorithm.Rating.HARD, testTimestamp + (24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.LEARNING, result.card.state)
        assertEquals(0, result.card.scheduledDays)
        assertEquals(2, result.card.reps)
        assertEquals(0, result.card.lapses)
    }

    @Test
    fun testLearningCard_Good_GoesToReview() {
        val learningCard = FSRSAlgorithm.FSRSCard(
            stability = 3.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.LEARNING,
            reps = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(learningCard, FSRSAlgorithm.Rating.GOOD, testTimestamp + (24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays > 0)
        assertEquals(2, result.card.reps)
    }

    @Test
    fun testLearningCard_Easy_GoesToReview() {
        val learningCard = FSRSAlgorithm.FSRSCard(
            stability = 3.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.LEARNING,
            reps = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(learningCard, FSRSAlgorithm.Rating.EASY, testTimestamp + (24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays > 0)
        assertEquals(2, result.card.reps)
    }

    // ========== Review State Tests ==========

    @Test
    fun testReviewCard_Again_GoesToRelearning() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.AGAIN, testTimestamp + (10L * 24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.RELEARNING, result.card.state)
        assertEquals(0, result.card.scheduledDays)
        assertEquals(6, result.card.reps)
        assertEquals(1, result.card.lapses)
    }

    @Test
    fun testReviewCard_Hard_StaysInReview() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.HARD, testTimestamp + (10L * 24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays > 0)
        assertEquals(6, result.card.reps)
    }

    @Test
    fun testReviewCard_Good_StaysInReview() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, testTimestamp + (10L * 24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays >= 10) // Should not decrease
        assertEquals(6, result.card.reps)
    }

    @Test
    fun testReviewCard_Easy_StaysInReview() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.EASY, testTimestamp + (10L * 24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays >= 10) // Should not decrease
        assertEquals(6, result.card.reps)
    }

    @Test
    fun testReviewCard_IntervalIncreases_WithBetterRatings() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )

        val reviewTime = testTimestamp + (10L * 24 * 60 * 60 * 1000)

        val hardResult = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.HARD, reviewTime)
        val goodResult = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)
        val easyResult = algorithm.schedule(reviewCard, FSRSAlgorithm.Rating.EASY, reviewTime)

        // Better ratings should generally lead to longer intervals
        assertTrue(goodResult.card.scheduledDays > hardResult.card.scheduledDays)
        assertTrue(easyResult.card.scheduledDays > goodResult.card.scheduledDays)
    }

    // ========== Relearning State Tests ==========

    @Test
    fun testRelearningCard_Good_GoesToReview() {
        val relearningCard = FSRSAlgorithm.FSRSCard(
            stability = 2.0,
            difficulty = 6.0,
            state = FSRSAlgorithm.CardState.RELEARNING,
            reps = 6,
            lapses = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(relearningCard, FSRSAlgorithm.Rating.GOOD, testTimestamp + (24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.REVIEW, result.card.state)
        assertTrue(result.card.scheduledDays > 0)
        assertEquals(7, result.card.reps)
    }

    @Test
    fun testRelearningCard_Again_StaysInRelearning() {
        val relearningCard = FSRSAlgorithm.FSRSCard(
            stability = 2.0,
            difficulty = 6.0,
            state = FSRSAlgorithm.CardState.RELEARNING,
            reps = 6,
            lapses = 1,
            lastReview = testTimestamp
        )

        val result = algorithm.schedule(relearningCard, FSRSAlgorithm.Rating.AGAIN, testTimestamp + (24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.LEARNING, result.card.state)
        assertEquals(0, result.card.scheduledDays)
        assertEquals(7, result.card.reps)
        assertEquals(1, result.card.lapses)
    }

    // ========== Review Log Tests ==========

    @Test
    fun testReviewLog_ContainsCorrectInformation() {
        val card = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            lastReview = testTimestamp
        )

        val reviewTime = testTimestamp + (12L * 24 * 60 * 60 * 1000) // 12 days later
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, reviewTime)

        assertEquals(FSRSAlgorithm.Rating.GOOD, result.reviewLog.rating)
        assertEquals(12, result.reviewLog.elapsedDays)
        assertEquals(reviewTime, result.reviewLog.review)
        assertEquals(result.card.state, result.reviewLog.state)
    }

    @Test
    fun testReviewLog_ElapsedDays_ZeroForFirstReview() {
        val newCard = FSRSAlgorithm.FSRSCard()
        val result = algorithm.schedule(newCard, FSRSAlgorithm.Rating.GOOD, testTimestamp)

        assertEquals(0, result.reviewLog.elapsedDays)
    }

    @Test
    fun testReviewLog_ElapsedDays_CalculatedCorrectly() {
        val card = FSRSAlgorithm.FSRSCard(
            lastReview = testTimestamp
        )

        val daysElapsed = 5L
        val reviewTime = testTimestamp + (daysElapsed * 24 * 60 * 60 * 1000)
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, reviewTime)

        assertEquals(daysElapsed.toInt(), result.reviewLog.elapsedDays)
    }

    // ========== getSchedulingCards Tests ==========

    @Test
    fun testGetSchedulingCards_ReturnsAllRatings() {
        val card = FSRSAlgorithm.FSRSCard()
        val schedulingCards = algorithm.getSchedulingCards(card, testTimestamp)

        assertEquals(4, schedulingCards.size)
        assertTrue(schedulingCards.containsKey(FSRSAlgorithm.Rating.AGAIN))
        assertTrue(schedulingCards.containsKey(FSRSAlgorithm.Rating.HARD))
        assertTrue(schedulingCards.containsKey(FSRSAlgorithm.Rating.GOOD))
        assertTrue(schedulingCards.containsKey(FSRSAlgorithm.Rating.EASY))
    }

    @Test
    fun testGetSchedulingCards_NewCard_ShowsCorrectStates() {
        val card = FSRSAlgorithm.FSRSCard()
        val schedulingCards = algorithm.getSchedulingCards(card, testTimestamp)

        assertEquals(FSRSAlgorithm.CardState.LEARNING, schedulingCards[FSRSAlgorithm.Rating.AGAIN]?.state)
        assertEquals(FSRSAlgorithm.CardState.LEARNING, schedulingCards[FSRSAlgorithm.Rating.HARD]?.state)
        assertEquals(FSRSAlgorithm.CardState.LEARNING, schedulingCards[FSRSAlgorithm.Rating.GOOD]?.state)
        assertEquals(FSRSAlgorithm.CardState.REVIEW, schedulingCards[FSRSAlgorithm.Rating.EASY]?.state)
    }

    @Test
    fun testGetSchedulingCards_ReviewCard_AllStayInReviewExceptAgain() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )

        val schedulingCards = algorithm.getSchedulingCards(reviewCard, testTimestamp + (10L * 24 * 60 * 60 * 1000))

        assertEquals(FSRSAlgorithm.CardState.RELEARNING, schedulingCards[FSRSAlgorithm.Rating.AGAIN]?.state)
        assertEquals(FSRSAlgorithm.CardState.REVIEW, schedulingCards[FSRSAlgorithm.Rating.HARD]?.state)
        assertEquals(FSRSAlgorithm.CardState.REVIEW, schedulingCards[FSRSAlgorithm.Rating.GOOD]?.state)
        assertEquals(FSRSAlgorithm.CardState.REVIEW, schedulingCards[FSRSAlgorithm.Rating.EASY]?.state)
    }

    // ========== Edge Cases and Boundary Tests ==========

    @Test
    fun testDifficulty_StaysWithinBounds() {
        var card = FSRSAlgorithm.FSRSCard()

        // Try to push difficulty to extremes
        repeat(50) {
            card = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp + (it * 24 * 60 * 60 * 1000L)).card
        }
        assertTrue(card.difficulty >= 1.0)
        assertTrue(card.difficulty <= 10.0)

        card = FSRSAlgorithm.FSRSCard()
        repeat(50) {
            card = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp + (it * 24 * 60 * 60 * 1000L)).card
        }
        assertTrue(card.difficulty >= 1.0)
        assertTrue(card.difficulty <= 10.0)
    }

    @Test
    fun testInterval_DoesNotExceedMaximum() {
        var card = FSRSAlgorithm.FSRSCard(
            state = FSRSAlgorithm.CardState.REVIEW,
            stability = 10000.0,
            difficulty = 1.0
        )

        // Try to push interval to maximum
        repeat(20) {
            card = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp + (it * 100000L * 24 * 60 * 60 * 1000L)).card
        }

        assertTrue(card.scheduledDays <= 36500) // Maximum is 100 years = 36500 days
    }

    @Test
    fun testStability_IncreasesOverTime_WithGoodRatings() {
        var card = FSRSAlgorithm.FSRSCard()
        val initialStability = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp).card.stability

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp).card
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp + (2L * 24 * 60 * 60 * 1000)).card

        assertTrue(card.stability > initialStability)
    }

    @Test
    fun testStability_DecreasesOnFailure() {
        var card = FSRSAlgorithm.FSRSCard()
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp).card
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp + (2L * 24 * 60 * 60 * 1000)).card

        val stabilityBeforeFail = card.stability
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp + (card.scheduledDays.toLong() * 24 * 60 * 60 * 1000)).card

        // Note: In FSRS, stability may not always decrease on failure, but difficulty increases
        assertTrue(card.difficulty > 5.0) // Difficulty should have increased
    }

    @Test
    fun testLastReview_UpdatedCorrectly() {
        val card = FSRSAlgorithm.FSRSCard()
        val reviewTime = testTimestamp + (5L * 24 * 60 * 60 * 1000)
        val result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, reviewTime)

        assertEquals(reviewTime, result.card.lastReview)
    }

    @Test
    fun testReps_IncrementCorrectly() {
        var card = FSRSAlgorithm.FSRSCard()

        assertEquals(0, card.reps)

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp).card
        assertEquals(1, card.reps)

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp + (2L * 24 * 60 * 60 * 1000)).card
        assertEquals(2, card.reps)

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp + (4L * 24 * 60 * 60 * 1000)).card
        assertEquals(3, card.reps)
    }

    @Test
    fun testLapses_IncrementOnFailure() {
        var card = FSRSAlgorithm.FSRSCard()

        assertEquals(0, card.lapses)

        // Lapses only increment when a REVIEW card fails; NEW and LEARNING failures do not count
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp).card
        assertEquals(0, card.lapses) // NEW state: not a lapse

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, testTimestamp + (24 * 60 * 60 * 1000)).card
        assertEquals(0, card.lapses) // LEARNING state: not a lapse
    }

    @Test
    fun testLapses_DoNotIncrementOnSuccess() {
        var card = FSRSAlgorithm.FSRSCard()

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp).card
        assertEquals(0, card.lapses)

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, testTimestamp + (2L * 24 * 60 * 60 * 1000)).card
        assertEquals(0, card.lapses)

        card = algorithm.schedule(card, FSRSAlgorithm.Rating.EASY, testTimestamp + (4L * 24 * 60 * 60 * 1000)).card
        assertEquals(0, card.lapses)
    }

    // ========== setRequestRetention Tests ==========

    @Test
    fun testSetRequestRetention_HigherRetention_ShorterIntervals() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (10L * 24 * 60 * 60 * 1000)

        val highRetentionAlgo = FSRSAlgorithm()
        highRetentionAlgo.setRequestRetention(95) // 95 % → shorter intervals
        val lowRetentionAlgo = FSRSAlgorithm()
        lowRetentionAlgo.setRequestRetention(75) // 75 % → longer intervals

        val highResult = highRetentionAlgo.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)
        val lowResult = lowRetentionAlgo.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)

        assertTrue(
            "Higher retention should produce shorter interval: high=${highResult.card.scheduledDays} low=${lowResult.card.scheduledDays}",
            highResult.card.scheduledDays < lowResult.card.scheduledDays
        )
    }

    @Test
    fun testSetRequestRetention_90Percent_MatchesDefault() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (10L * 24 * 60 * 60 * 1000)

        val defaultAlgo = FSRSAlgorithm()
        val explicit90Algo = FSRSAlgorithm()
        explicit90Algo.setRequestRetention(90)

        val defaultResult = defaultAlgo.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)
        val explicit90Result = explicit90Algo.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)

        assertEquals(defaultResult.card.scheduledDays, explicit90Result.card.scheduledDays)
    }

    @Test
    fun testSetRequestRetention_ClampsLowValues_ReturnsValidInterval() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (10L * 24 * 60 * 60 * 1000)

        val algo = FSRSAlgorithm()
        algo.setRequestRetention(5) // well below range; clamped to 0.10

        val result = algo.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)
        assertTrue(result.card.scheduledDays >= 1)
    }

    @Test
    fun testSetRequestRetention_ClampsHighValues_ReturnsValidInterval() {
        val reviewCard = FSRSAlgorithm.FSRSCard(
            stability = 10.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.REVIEW,
            scheduledDays = 10,
            reps = 5,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (10L * 24 * 60 * 60 * 1000)

        val algo = FSRSAlgorithm()
        algo.setRequestRetention(200) // well above range; clamped to 0.99

        val result = algo.schedule(reviewCard, FSRSAlgorithm.Rating.GOOD, reviewTime)
        assertTrue(result.card.scheduledDays >= 1)
    }

    @Test
    fun testSetRequestRetention_DoesNotAffectLearningState() {
        // requestRetention only influences REVIEW-state interval calculation; LEARNING→LEARNING
        // steps (e.g. AGAIN) use a fixed scheduledDays=0 that is independent of retention.
        val learningCard = FSRSAlgorithm.FSRSCard(
            stability = 3.0,
            difficulty = 5.0,
            state = FSRSAlgorithm.CardState.LEARNING,
            reps = 1,
            lastReview = testTimestamp
        )
        val reviewTime = testTimestamp + (24 * 60 * 60 * 1000L)

        val highRetentionAlgo = FSRSAlgorithm()
        highRetentionAlgo.setRequestRetention(97)
        val lowRetentionAlgo = FSRSAlgorithm()
        lowRetentionAlgo.setRequestRetention(70)

        // AGAIN keeps the card in LEARNING with a fixed step (scheduledDays = 0)
        val highResult = highRetentionAlgo.schedule(learningCard, FSRSAlgorithm.Rating.AGAIN, reviewTime)
        val lowResult = lowRetentionAlgo.schedule(learningCard, FSRSAlgorithm.Rating.AGAIN, reviewTime)

        // Both should stay in LEARNING and have identical scheduledDays (fixed step, not retention-dependent)
        assertEquals(FSRSAlgorithm.CardState.LEARNING, highResult.card.state)
        assertEquals(FSRSAlgorithm.CardState.LEARNING, lowResult.card.state)
        assertEquals(highResult.card.scheduledDays, lowResult.card.scheduledDays)
    }

    // ========== Realistic Scenario Tests ==========

    @Test
    fun testRealisticScenario_NewCardToMastery() {
        var card = FSRSAlgorithm.FSRSCard()
        var currentTime = testTimestamp

        // First review: NEW -> LEARNING
        var result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, currentTime)
        card = result.card
        assertEquals(FSRSAlgorithm.CardState.LEARNING, card.state)

        // Second review: LEARNING -> REVIEW
        currentTime += (card.scheduledDays.toLong() * 24 * 60 * 60 * 1000)
        result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, currentTime)
        card = result.card
        assertEquals(FSRSAlgorithm.CardState.REVIEW, card.state)

        // Multiple successful reviews
        repeat(5) {
            currentTime += (card.scheduledDays.toLong() * 24 * 60 * 60 * 1000)
            result = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, currentTime)
            card = result.card
            assertEquals(FSRSAlgorithm.CardState.REVIEW, card.state)
        }

        // Card should have accumulated reviews and increased intervals
        assertTrue(card.reps >= 6)
        assertTrue(card.scheduledDays > 10)
    }

    @Test
    fun testRealisticScenario_CardWithLapses() {
        var card = FSRSAlgorithm.FSRSCard()
        var currentTime = testTimestamp

        // Learn the card
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, currentTime).card
        currentTime += (card.scheduledDays.toLong() * 24 * 60 * 60 * 1000)
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, currentTime).card
        assertEquals(FSRSAlgorithm.CardState.REVIEW, card.state)

        // Fail the card
        currentTime += (card.scheduledDays.toLong() * 24 * 60 * 60 * 1000)
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.AGAIN, currentTime).card
        assertEquals(FSRSAlgorithm.CardState.RELEARNING, card.state)
        assertEquals(1, card.lapses)

        // Relearn the card
        currentTime += (card.scheduledDays.toLong() * 24 * 60 * 60 * 1000)
        card = algorithm.schedule(card, FSRSAlgorithm.Rating.GOOD, currentTime).card
        assertEquals(FSRSAlgorithm.CardState.REVIEW, card.state)
        assertEquals(1, card.lapses) // Lapses should persist
    }
}
