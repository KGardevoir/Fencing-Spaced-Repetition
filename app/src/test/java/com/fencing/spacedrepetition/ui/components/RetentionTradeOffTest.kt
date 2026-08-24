// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the plain-language trade-off estimates shown by the retention selector.
 */
class RetentionTradeOffTest {

    @Test
    fun `interval factor decreases as retention increases`() {
        var previous = Double.MAX_VALUE
        for (percent in ThemePreferences.MIN_FSRS_RETENTION..ThemePreferences.MAX_FSRS_RETENTION) {
            val factor = RetentionTradeOff.intervalFactor(percent)
            assertTrue("Interval factor at $percent % must be positive", factor > 0)
            assertTrue("Interval factor must shrink as retention rises ($percent %)", factor < previous)
            previous = factor
        }
    }

    @Test
    fun `workload multiplier is 1 at the baseline`() {
        assertEquals(1.0, RetentionTradeOff.workloadMultiplier(RetentionTradeOff.BASELINE_PERCENT), 1e-9)
    }

    @Test
    fun `workload multiplier is below 1 for lower retention`() {
        assertTrue(RetentionTradeOff.workloadMultiplier(80) < 1.0)
        assertTrue(RetentionTradeOff.workloadMultiplier(70) < RetentionTradeOff.workloadMultiplier(80))
    }

    @Test
    fun `workload multiplier is above 1 for higher retention`() {
        assertTrue(RetentionTradeOff.workloadMultiplier(95) > 1.0)
        assertTrue(RetentionTradeOff.workloadMultiplier(97) > RetentionTradeOff.workloadMultiplier(95))
    }

    @Test
    fun `summary at baseline describes the recommended balance`() {
        val summary = RetentionTradeOff.summary(90)
        assertTrue(summary.contains("10 in 100"))
        assertTrue(summary.contains("recommended balance"))
    }

    @Test
    fun `summary below baseline mentions fewer reviews`() {
        val summary = RetentionTradeOff.summary(80)
        assertTrue(summary.contains("20 in 100"))
        assertTrue(summary.contains("fewer reviews"))
    }

    @Test
    fun `summary above baseline mentions the review multiplier`() {
        val summary = RetentionTradeOff.summary(95)
        assertTrue(summary.contains("5 in 100"))
        assertTrue(summary.contains("× the reviews"))
    }

    @Test
    fun `summary never crashes across the valid range`() {
        for (percent in ThemePreferences.MIN_FSRS_RETENTION..ThemePreferences.MAX_FSRS_RETENTION) {
            assertTrue(RetentionTradeOff.summary(percent).isNotBlank())
        }
    }
}
