// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs on every target, which is the point: this replaces a JVM-only API and
 * has to agree with it on the JVM while also working in a browser.
 */
class DecimalsTest {

    @Test
    fun padsToTwoPlaces() {
        assertEquals("1.00", 1.0.toTwoDecimals())
        assertEquals("0.50", 0.5.toTwoDecimals())
        assertEquals("2.00", 2.0.toTwoDecimals())
        assertEquals("0.10", 0.1.toTwoDecimals())
    }

    @Test
    fun keepsTwoPlacesItAlreadyHas() {
        assertEquals("1.25", 1.25.toTwoDecimals())
        assertEquals("0.75", 0.75.toTwoDecimals())
        assertEquals("2.50", 2.5.toTwoDecimals())
    }

    @Test
    fun roundsRatherThanTruncates() {
        assertEquals("1.24", 1.238.toTwoDecimals())
        assertEquals("1.24", 1.235.toTwoDecimals())
        assertEquals("1.23", 1.234.toTwoDecimals())
        assertEquals("3.00", 2.999.toTwoDecimals())
    }

    @Test
    fun carriesIntoTheWholePart() {
        assertEquals("1.00", 0.999.toTwoDecimals())
        assertEquals("10.00", 9.9999.toTwoDecimals())
    }

    @Test
    fun keepsTheSignOnSmallNegatives() {
        // The naive version loses the minus here, because the whole part
        // rounds to zero and the sign lives only in the fraction.
        assertEquals("-0.50", (-0.5).toTwoDecimals())
        assertEquals("-0.05", (-0.05).toTwoDecimals())
        assertEquals("-1.25", (-1.25).toTwoDecimals())
    }

    @Test
    fun handlesZero() {
        assertEquals("0.00", 0.0.toTwoDecimals())
    }
}
