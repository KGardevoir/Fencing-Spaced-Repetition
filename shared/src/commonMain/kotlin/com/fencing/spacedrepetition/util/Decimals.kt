// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats a number to exactly two decimal places.
 *
 * This exists because String.format is a JVM API. Every screen in this app
 * displays skill multipliers, ease factors and retention percentages through
 * `"%.2f".format(x)`, and each one of those is a compile error the moment the
 * screen is shared with the browser -- so the replacement lives here, once,
 * rather than being rediscovered per screen.
 *
 * Rounding is half away from zero, which is what "%.2f" does for the positive
 * quantities this app formats. Values beyond about 9e16 exceed what the
 * scaled arithmetic can hold, so they fall back to the platform's own
 * rendering; nothing in this app comes close, and silently printing a wrong
 * number would be worse than an unusual-looking one.
 */
fun Double.toTwoDecimals(): String = toFixed(places = 2, scale = 100)

/**
 * Formats a number to exactly one decimal place -- the `"%.1f"` sites.
 *
 * The card list shows a difficulty this way, and for SM-2 it shows
 * `2.5 - easeFactor`, which goes negative for an easy card. That is why the
 * sign comes from the value and not from the rounded result: -0.04 rounds to
 * zero at one place, and "%.1f" still prints it as "-0.0".
 */
fun Double.toOneDecimal(): String = toFixed(places = 1, scale = 10)

private fun Double.toFixed(places: Int, scale: Long): String {
    if (isNaN() || isInfinite()) return toString()
    if (abs(this) > 9e16) return toString()

    // The magnitude is rounded, not the signed value: roundToLong rounds half
    // *up*, so -12.5 would become -12 and print "-1.2" where "%.1f" prints
    // "-1.3". Rounding abs() first gives half away from zero on both sides.
    val magnitude = abs(this * scale).roundToLong()
    val sign = if (this < 0.0) "-" else ""
    val fraction = (magnitude % scale).toString().padStart(places, '0')
    return "$sign${magnitude / scale}.$fraction"
}
