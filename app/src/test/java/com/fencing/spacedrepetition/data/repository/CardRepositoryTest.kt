// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CardRepositoryTest {

    // ==================== forwardDaysToNearestPracticeDay TESTS ====================

    @Test
    fun `forwardDaysToNearestPracticeDay - target already in practice days returns 0`() {
        // Wednesday is already a practice day — no rounding needed
        val result = CardRepository.forwardDaysToNearestPracticeDay(3, setOf(1, 3, 5))
        assertEquals(0, result)
    }

    @Test
    fun `forwardDaysToNearestPracticeDay - rounds forward to closest practice day`() {
        // Target Wednesday (3), practice days Mon (1) and Fri (5)
        // Forward to Fri: (5-3+7)%7 = 2; Forward to Mon: (1-3+7)%7 = 5
        // Closest forward is Friday (+2)
        val result = CardRepository.forwardDaysToNearestPracticeDay(3, setOf(1, 5))
        assertEquals(2, result)
    }

    @Test
    fun `forwardDaysToNearestPracticeDay - never rounds backward even when backward is closer`() {
        // Target Friday (5), practice days Mon (1) and Wed (3)
        // Backward to Wed: 2 days — old algorithm would pick this
        // Forward to Mon: (1-5+7)%7 = 3 days — must always go forward
        val result = CardRepository.forwardDaysToNearestPracticeDay(5, setOf(1, 3))
        assertEquals(3, result)
    }

    @Test
    fun `forwardDaysToNearestPracticeDay - never rounds backward when backward by 1 day`() {
        // Target Thursday (4), practice days Wed (3) and Sat (6)
        // Backward to Wed: 1 day — old algorithm would pick this
        // Forward to Sat: (6-4+7)%7 = 2 days — must go forward
        val result = CardRepository.forwardDaysToNearestPracticeDay(4, setOf(3, 6))
        assertEquals(2, result)
    }

    @Test
    fun `forwardDaysToNearestPracticeDay - single practice day same day`() {
        val result = CardRepository.forwardDaysToNearestPracticeDay(5, setOf(5))
        assertEquals(0, result)
    }

    @Test
    fun `forwardDaysToNearestPracticeDay - single practice day wraps around week`() {
        // Target Sunday (7), only practice day Tuesday (2)
        // Forward: (2-7+7)%7 = 2 days
        val result = CardRepository.forwardDaysToNearestPracticeDay(7, setOf(2))
        assertEquals(2, result)
    }

    @Test
    fun `forwardDaysToNearestPracticeDay - picks nearest of multiple forward options`() {
        // Target Monday (1), practice days Wed (3), Fri (5), Sun (7)
        // Forward: Wed +2, Fri +4, Sun +6 — nearest is Wed
        val result = CardRepository.forwardDaysToNearestPracticeDay(1, setOf(3, 5, 7))
        assertEquals(2, result)
    }
}
