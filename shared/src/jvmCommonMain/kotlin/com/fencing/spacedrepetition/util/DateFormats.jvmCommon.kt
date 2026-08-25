// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In jvmCommonMain rather than androidMain, next to Time's actual and for the
 * same reason: SimpleDateFormat is a Java API, so this one implementation
 * serves both the Android build and the JVM target the fast tests run on.
 * Putting it in androidMain would leave the JVM target with no actual at all.
 */
// Built per call rather than cached: SimpleDateFormat is not thread-safe, and
// per call is what the screens did before these patterns moved here.
private fun format(pattern: String, epochMillis: Long): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))

actual fun formatDate(epochMillis: Long, padDay: Boolean): String =
    format(if (padDay) "MMM dd, yyyy" else "MMM d, yyyy", epochMillis)

actual fun formatDateWithoutYear(epochMillis: Long, padDay: Boolean): String =
    format(if (padDay) "MMM dd" else "MMM d", epochMillis)

actual fun formatTimeOfDay(epochMillis: Long): String =
    format("h:mm a", epochMillis)
