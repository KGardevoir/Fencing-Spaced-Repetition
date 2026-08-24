// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class CardRepositoryTest {

    // ==================== isoDayOfWeekAfter TESTS ====================

    // 2026-08-23T12:00:00Z is a Sunday (ISO day 7).
    private val sundayNoonUtc = 1787486400000L

    @Test
    fun `isoDayOfWeekAfter - zero days returns today`() {
        assertEquals(7, CardRepository.isoDayOfWeekAfter(sundayNoonUtc, 0, 0))
    }

    @Test
    fun `isoDayOfWeekAfter - walks forward through the week`() {
        listOf(1, 2, 3, 4, 5, 6).forEachIndexed { i, dow ->
            assertEquals(dow, CardRepository.isoDayOfWeekAfter(sundayNoonUtc, i + 1, 0))
        }
    }

    @Test
    fun `isoDayOfWeekAfter - wraps around a full week`() {
        assertEquals(7, CardRepository.isoDayOfWeekAfter(sundayNoonUtc, 7, 0))
        assertEquals(1, CardRepository.isoDayOfWeekAfter(sundayNoonUtc, 8, 0))
    }

    @Test
    fun `isoDayOfWeekAfter - long intervals stay correct`() {
        // 364 days is exactly 52 weeks, so the weekday must be unchanged.
        assertEquals(7, CardRepository.isoDayOfWeekAfter(sundayNoonUtc, 364, 0))
        assertEquals(1, CardRepository.isoDayOfWeekAfter(sundayNoonUtc, 365, 0))
    }

    @Test
    fun `isoDayOfWeekAfter - negative offset can pull back to the previous day`() {
        // 2026-08-24T01:00:00Z is Monday at UTC but still Sunday at UTC-05:00.
        val mondayEarlyUtc = 1787533200000L
        assertEquals(1, CardRepository.isoDayOfWeekAfter(mondayEarlyUtc, 0, 0))
        assertEquals(7, CardRepository.isoDayOfWeekAfter(mondayEarlyUtc, 0, -5 * 3600))
    }

    @Test
    fun `isoDayOfWeekAfter - positive offset can push on to the next day`() {
        // 2026-08-23T23:00:00Z is Sunday at UTC but already Monday at UTC+09:00.
        val sundayLateUtc = 1787526000000L
        assertEquals(7, CardRepository.isoDayOfWeekAfter(sundayLateUtc, 0, 0))
        assertEquals(1, CardRepository.isoDayOfWeekAfter(sundayLateUtc, 0, 9 * 3600))
    }

    @Test
    fun `isoDayOfWeekAfter - handles instants before the epoch`() {
        // 1969-12-31T12:00:00Z was a Wednesday (ISO 3); the epoch itself a Thursday.
        assertEquals(3, CardRepository.isoDayOfWeekAfter(-43200000L, 0, 0))
        assertEquals(4, CardRepository.isoDayOfWeekAfter(0L, 0, 0))
    }

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
