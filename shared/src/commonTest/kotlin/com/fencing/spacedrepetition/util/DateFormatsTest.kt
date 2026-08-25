// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Runs on the JVM and in a browser, which is the point: these are two entirely
 * separate implementations -- SimpleDateFormat and Intl.DateTimeFormat -- and
 * this is the only thing holding them to the same contract.
 *
 * Every assertion here is structural, and that is a deliberate constraint
 * rather than laziness. Asserting "Aug 5, 2026" would be asserting that CI
 * runs in English, in a Gregorian locale, west of the date line: a Thai locale
 * renders that year as 2569, a UTC+14 machine renders that day as the 6th, and
 * none of that would be the code being wrong. What can be checked anywhere is
 * that the pieces are present, that the options do what they claim, and that
 * the composed forms are exactly their parts.
 */
class DateFormatsTest {

    /** 2026-08-05T15:07:00Z. Mid-month, so the day is single-digit worldwide. */
    private val instant = 1_785_942_420_000L

    private val oneHour = 60L * 60 * 1000
    private val oneDay = 24 * oneHour

    @Test
    fun theShortFormDropsSomethingTheLongFormHas() {
        // Whatever the locale calls the year, asking for it makes the string
        // longer -- without this test the two could quietly be identical.
        assertTrue(
            formatDate(instant).length > formatDateWithoutYear(instant).length,
            "'${formatDate(instant)}' was not longer than '${formatDateWithoutYear(instant)}'"
        )
    }

    @Test
    fun paddingAddsExactlyOneCharacterToASingleDigitDay() {
        val padded = formatDateWithoutYear(instant, padDay = true)
        val plain = formatDateWithoutYear(instant, padDay = false)

        assertEquals(
            plain.length + 1, padded.length,
            "padded '$padded' should be one longer than unpadded '$plain'"
        )
    }

    @Test
    fun theTimeHasHoursAndMinutes() {
        val time = formatTimeOfDay(instant)
        assertTrue(":" in time, "no time separator in '$time'")
    }

    @Test
    fun theInstantIsReadAsMilliseconds() {
        // A day later must read as a different date and an hour later as a
        // different time. Both fail loudly if the epoch is ever taken as
        // seconds, and neither depends on how the locale spells anything.
        assertNotEquals(formatDate(instant), formatDate(instant + oneDay))
        assertNotEquals(formatTimeOfDay(instant), formatTimeOfDay(instant + oneHour))
    }

    @Test
    fun composedFormsAreExactlyTheirParts() {
        val date = formatDate(instant, padDay = false)
        val time = formatTimeOfDay(instant)

        assertEquals("$date $time", formatDateAndTime(instant))
        assertEquals("$date at $time", formatDateAtTime(instant))
    }
}
