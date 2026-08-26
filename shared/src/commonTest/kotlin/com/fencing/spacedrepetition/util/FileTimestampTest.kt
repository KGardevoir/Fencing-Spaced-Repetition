// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The stamp on every file the app writes.
 *
 * Calendar arithmetic written by hand deserves tests: the failure mode is not
 * a crash but a filename claiming the wrong day, which nobody notices until
 * they are looking for last Tuesday's export. The vectors below are fixed
 * instants with the answer worked out independently, rather than a second
 * implementation of the same algorithm.
 */
class FileTimestampTest {

    private val hour = 3600

    /** 2023-11-14T22:13:20Z, the round number that gets used everywhere. */
    private val reference = 1_700_000_000_000L

    @Test
    fun theEpochItself() {
        assertEquals("1970-01-01_00-00-00", fileTimestamp(0L, utcOffsetSeconds = 0))
    }

    @Test
    fun aKnownInstantInUtc() {
        assertEquals("2023-11-14_22-13-20", fileTimestamp(reference, utcOffsetSeconds = 0))
    }

    @Test
    fun theOffsetIsAppliedRatherThanIgnored() {
        assertEquals("2023-11-14_14-13-20", fileTimestamp(reference, utcOffsetSeconds = -8 * hour))
        assertEquals("2023-11-15_09-13-20", fileTimestamp(reference, utcOffsetSeconds = 11 * hour))
    }

    /** East of Greenwich late in the evening is already tomorrow. */
    @Test
    fun anOffsetCanCarryIntoTheNextDay() {
        assertEquals("2023-11-15_01-13-20", fileTimestamp(reference, utcOffsetSeconds = 3 * hour))
    }

    /** Every field is padded, so the names are the same width and sort. */
    @Test
    fun singleDigitFieldsArePadded() {
        // 2021-03-04T05:06:07Z
        assertEquals("2021-03-04_05-06-07", fileTimestamp(1_614_834_367_000L, utcOffsetSeconds = 0))
    }

    @Test
    fun leapDaysAreRealDays() {
        // 2024-02-29T12:00:00Z
        assertEquals("2024-02-29_12-00-00", fileTimestamp(1_709_208_000_000L, utcOffsetSeconds = 0))
    }

    /**
     * A device whose clock is set before 1970. Absurd, and the arithmetic
     * either handles it or produces a name with a negative number in it.
     */
    @Test
    fun instantsBeforeTheEpoch() {
        assertEquals("1969-12-31_23-59-59", fileTimestamp(-1_000L, utcOffsetSeconds = 0))
        assertEquals("1969-07-20_20-17-40", fileTimestamp(-14_182_940_000L, utcOffsetSeconds = 0))
    }

    /** Sorting by name is sorting by time, which is the whole point of the shape. */
    @Test
    fun namesSortInTimeOrder() {
        val earlier = fileTimestamp(reference, utcOffsetSeconds = 0)
        val later = fileTimestamp(reference + 1_000L, utcOffsetSeconds = 0)
        val muchLater = fileTimestamp(reference + 400L * 24 * 60 * 60 * 1000, utcOffsetSeconds = 0)

        assertEquals(listOf(earlier, later, muchLater), listOf(muchLater, earlier, later).sorted())
    }
}
