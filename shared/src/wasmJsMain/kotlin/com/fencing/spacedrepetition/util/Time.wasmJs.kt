// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

/**
 * `Date.prototype.getTimezoneOffset` returns minutes *west* of UTC, so a zone
 * ahead of Greenwich reports a negative number. The rest of the app uses the
 * opposite convention -- positive east -- which is why the result is negated.
 */
private fun timezoneOffsetMinutesWestOfUtc(epochMillis: Double): Int =
    js("new Date(epochMillis).getTimezoneOffset()")

internal actual fun platformUtcOffsetSeconds(atEpochMillis: Long): Int =
    -timezoneOffsetMinutesWestOfUtc(atEpochMillis.toDouble()) * 60
