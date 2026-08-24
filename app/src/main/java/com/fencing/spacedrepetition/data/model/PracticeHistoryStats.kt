// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

/**
 * Aggregate practice activity sampled from review logs within a time window.
 *
 * @property totalReviews Number of card reviews logged in the window.
 * @property practiceDays Number of distinct local-time days with at least one review.
 * @property firstReviewTime Epoch millis of the earliest review in the window (0 when empty).
 */
data class PracticeHistoryStats(
    val totalReviews: Int,
    val practiceDays: Int,
    val firstReviewTime: Long
)
