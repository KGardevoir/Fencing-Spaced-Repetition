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
fun Double.toTwoDecimals(): String {
    if (isNaN() || isInfinite()) return toString()
    if (abs(this) > 9e16) return toString()

    val scaled = (this * 100).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val fraction = (magnitude % 100).toString().padStart(2, '0')
    return "$sign${magnitude / 100}.$fraction"
}
