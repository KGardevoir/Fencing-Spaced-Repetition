package com.fencing.spacedrepetition.algorithm

import com.fencing.spacedrepetition.data.model.PracticeHistoryStats
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPlannerTest {

    private val dayMs = 24L * 60 * 60 * 1000

    // ==================== suggestedRetention TESTS ====================

    @Test
    fun `schedule that reviews each card every 30 days suggests the 90 percent anchor`() {
        // 30 cards at 7 days × 1 set = 7 sets/week → each card reviewed every 30 days,
        // which is exactly the typical stability the planner anchors to R = 90 %.
        assertEquals(90, RetentionPlanner.suggestedRetention(7.0, 1.0, 30))
    }

    @Test
    fun `more cards on the same schedule suggests lower retention`() {
        val schedule = { cards: Int -> RetentionPlanner.suggestedRetention(3.0, 5.0, cards) }
        assertTrue(schedule(200) < schedule(50))
        assertTrue(schedule(50) <= schedule(10))
    }

    @Test
    fun `more weekly capacity suggests higher retention`() {
        assertTrue(
            RetentionPlanner.suggestedRetention(6.0, 10.0, 100) >
                RetentionPlanner.suggestedRetention(2.0, 3.0, 100)
        )
    }

    @Test
    fun `suggestion is always within the app's valid retention range`() {
        val extremes = listOf(
            RetentionPlanner.suggestedRetention(7.0, 99.0, 1),    // huge capacity, one card
            RetentionPlanner.suggestedRetention(1.0, 1.0, 10000), // tiny capacity, many cards
            RetentionPlanner.suggestedRetention(0.0, 0.0, 0)      // degenerate input
        )
        extremes.forEach { suggestion ->
            assertTrue(
                "Suggestion $suggestion out of range",
                suggestion in ThemePreferences.MIN_FSRS_RETENTION..ThemePreferences.MAX_FSRS_RETENTION
            )
        }
    }

    @Test
    fun `history fit window bounds are sane`() {
        assertTrue(RetentionPlanner.MIN_HISTORY_WINDOW_DAYS < RetentionPlanner.MAX_HISTORY_WINDOW_DAYS)
        assertTrue(
            RetentionPlanner.DEFAULT_HISTORY_WINDOW_DAYS in
                RetentionPlanner.MIN_HISTORY_WINDOW_DAYS..RetentionPlanner.MAX_HISTORY_WINDOW_DAYS
        )
    }

    // ==================== estimateSchedule TESTS ====================

    @Test
    fun `estimate is null with no stats`() {
        assertNull(RetentionPlanner.estimateSchedule(null, 0L, 0L))
    }

    @Test
    fun `estimate is null with too few practice days`() {
        val now = 100L * dayMs
        val stats = PracticeHistoryStats(totalReviews = 10, practiceDays = 2, firstReviewTime = now - 14 * dayMs)
        assertNull(RetentionPlanner.estimateSchedule(stats, now, now - 56 * dayMs))
    }

    @Test
    fun `estimate uses the span since the first review`() {
        val now = 100L * dayMs
        val windowStart = now - 56 * dayMs
        // 12 practice days and 60 reviews over the 28 days since the first logged review
        val stats = PracticeHistoryStats(totalReviews = 60, practiceDays = 12, firstReviewTime = now - 28 * dayMs)
        val estimate = RetentionPlanner.estimateSchedule(stats, now, windowStart)!!
        assertEquals(3.0, estimate.daysPerWeek, 1e-9)
        assertEquals(5.0, estimate.setsPerPractice, 1e-9)
    }

    @Test
    fun `estimate span is clamped to at least one week`() {
        val now = 100L * dayMs
        // All history within the last 2 days must not extrapolate beyond 7 days/week
        val stats = PracticeHistoryStats(totalReviews = 30, practiceDays = 3, firstReviewTime = now - 2 * dayMs)
        val estimate = RetentionPlanner.estimateSchedule(stats, now, now - 56 * dayMs)!!
        assertEquals(3.0, estimate.daysPerWeek, 1e-9)
        assertEquals(10.0, estimate.setsPerPractice, 1e-9)
    }

    @Test
    fun `estimated days per week never exceeds 7`() {
        val now = 100L * dayMs
        val stats = PracticeHistoryStats(totalReviews = 400, practiceDays = 56, firstReviewTime = now - 56 * dayMs)
        val estimate = RetentionPlanner.estimateSchedule(stats, now, now - 56 * dayMs)!!
        assertTrue(estimate.daysPerWeek <= 7.0)
    }
}
