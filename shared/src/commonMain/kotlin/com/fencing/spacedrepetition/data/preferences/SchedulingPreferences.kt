// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * The settings the scheduler actually reads.
 *
 * CardRepository used to take ThemePreferences whole, which meant it depended
 * on DataStore and an Android Context to reach seven values. This is that
 * subset and nothing else, so the repository can live in common code while
 * each platform stores the settings however it likes -- DataStore on Android,
 * something browser-shaped on the web.
 *
 * Read-only on purpose: scheduling consumes these, it never writes them.
 */
interface SchedulingPreferences {
    /** Shuffle due cards instead of presenting them in due order. */
    val randomizeDueCards: Flow<Boolean>

    /** Width, in hours, of the bucket within which due cards are shuffled. */
    val randomizeBucketHours: Flow<Int>

    /** Upper bound on any scheduled interval, in days. */
    val maximumInterval: Flow<Int>

    /** ISO weekday numbers (Monday = 1) the user practises on. */
    val practiceDays: Flow<Set<Int>>

    /** FSRS desired retention, as a percentage. */
    val fsrsRetention: Flow<Int>

    /** Whether FSRS applies its interval fuzz. */
    val fsrsEnableFuzzing: Flow<Boolean>
}
