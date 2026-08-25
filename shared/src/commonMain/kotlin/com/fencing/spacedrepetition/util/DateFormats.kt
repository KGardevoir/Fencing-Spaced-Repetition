// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

/**
 * The date and time formats the screens show, once, for every platform.
 *
 * This is expect/actual rather than shared arithmetic on purpose. Month names
 * are localised, and the only correct source for them is the platform: Android
 * has SimpleDateFormat and a browser has Intl.DateTimeFormat, and both already
 * know what August is called wherever the user is. Formatting the month here
 * from a hardcoded English table would compile everywhere, pass any test
 * written in English, and quietly show the wrong words to everyone else.
 *
 * The patterns come from the Android screens, so Android's output does not
 * change at all. Two of them are worth naming:
 *
 *  - [padDay] exists because the screens are inconsistent: most use "MMM dd"
 *    and one uses "MMM d". Reproduced rather than tidied, so that moving a
 *    screen to shared code changes where it lives and not what it shows.
 *  - The clock is 12-hour on both platforms, because the Android pattern
 *    "h:mm a" always was regardless of locale. A browser would happily use
 *    the locale's own convention; making it do that is a real improvement and
 *    a separate one, and it should change both platforms together rather than
 *    letting the same app disagree with itself.
 */
expect fun formatDate(epochMillis: Long, padDay: Boolean = true): String

/** The same date without the year, for entries already grouped under one. */
expect fun formatDateWithoutYear(epochMillis: Long, padDay: Boolean = true): String

/** Time of day, 12-hour, with a localised AM/PM. */
expect fun formatTimeOfDay(epochMillis: Long): String

/** "Aug 5, 2026 3:07 PM" -- the Android "MMM d, yyyy h:mm a" pattern. */
fun formatDateAndTime(epochMillis: Long): String =
    formatDate(epochMillis, padDay = false) + " " + formatTimeOfDay(epochMillis)

/**
 * "Aug 5, 2026 at 3:07 PM" -- the Android "MMM d, yyyy 'at' h:mm a" pattern.
 *
 * The "at" was a quoted literal in that pattern, so it was never translated
 * on Android either; composing it here keeps the two platforms identical
 * rather than inventing a difference.
 */
fun formatDateAtTime(epochMillis: Long): String =
    formatDate(epochMillis, padDay = false) + " at " + formatTimeOfDay(epochMillis)
