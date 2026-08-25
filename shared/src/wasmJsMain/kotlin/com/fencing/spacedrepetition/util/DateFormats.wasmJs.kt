// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// Intl.DateTimeFormat with an undefined locale follows the browser's own
// locale, which is the counterpart of Locale.getDefault() on Android.
//
// Each of these is a single js() expression because that is what a Kotlin/Wasm
// js() body has to be, and takes a Double because Long has no direct JS
// representation.

private fun intlDate(millis: Double, day: String): String =
    js("new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: day }).format(new Date(millis))")

private fun intlDateWithoutYear(millis: Double, day: String): String =
    js("new Intl.DateTimeFormat(undefined, { month: 'short', day: day }).format(new Date(millis))")

// hour12 is forced to match Android's "h:mm a", which is 12-hour in every
// locale. See the note on the expect declarations.
private fun intlTime(millis: Double): String =
    js("new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', hour12: true }).format(new Date(millis))")

actual fun formatDate(epochMillis: Long, padDay: Boolean): String =
    intlDate(epochMillis.toDouble(), if (padDay) "2-digit" else "numeric")

actual fun formatDateWithoutYear(epochMillis: Long, padDay: Boolean): String =
    intlDateWithoutYear(epochMillis.toDouble(), if (padDay) "2-digit" else "numeric")

actual fun formatTimeOfDay(epochMillis: Long): String =
    intlTime(epochMillis.toDouble())
