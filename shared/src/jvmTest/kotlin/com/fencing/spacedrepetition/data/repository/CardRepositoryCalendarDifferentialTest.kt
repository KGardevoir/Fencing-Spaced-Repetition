// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The differential half of CardRepositoryTest, kept on the JVM because that is
 * where the implementation it checks against lives.
 *
 * isoDayOfWeekAfter replaced a java.util.Calendar computation. The rest of its
 * tests are assertions about what the answer should be; this one asserts that
 * the answer has not changed, which can only be done where Calendar exists.
 * Every other target runs the common tests only.
 */
class CardRepositoryCalendarDifferentialTest {

    // 2026-08-23T12:00:00Z is a Sunday (ISO day 7).
    private val sundayNoonUtc = 1787486400000L

    @Test
    fun `isoDayOfWeekAfter - agrees with java util Calendar across a year of offsets`() {
        // Differential test against the implementation this replaced, so any
        // divergence in the day-number arithmetic shows up here.
        val offsets = listOf(-11, -8, -5, 0, 1, 5, 9, 13).map { it * 3600 }
        for (offsetSeconds in offsets) {
            val zone = java.util.TimeZone.getTimeZone(
                java.time.ZoneOffset.ofTotalSeconds(offsetSeconds)
            )
            for (days in 0..370) {
                val calendar = java.util.Calendar.getInstance(zone)
                calendar.timeInMillis = sundayNoonUtc
                calendar.add(java.util.Calendar.DAY_OF_YEAR, days)
                val calendarDow = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                val expected = if (calendarDow == java.util.Calendar.SUNDAY) 7 else calendarDow - 1

                assertEquals(
                    expected,
                    CardRepository.isoDayOfWeekAfter(sundayNoonUtc, days, offsetSeconds),
                    "offset=$offsetSeconds days=$days"
                )
            }
        }
    }
}
