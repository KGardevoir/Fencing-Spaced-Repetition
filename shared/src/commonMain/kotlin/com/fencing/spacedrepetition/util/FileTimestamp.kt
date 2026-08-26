// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * `2026-08-26_14-05-09` -- the stamp every file this app writes begins with.
 *
 * Deliberately not one of the [formatDate] family. Those are for reading and
 * are localised, which is right on a screen and wrong in a filename: this one
 * has to sort, has to be the same shape for everyone, and has to survive
 * being a filename on any of the systems these files get copied between.
 * Hence ISO order, hyphens rather than colons, and a 24-hour clock -- 12-hour
 * would put two files an hour apart in the wrong order and give them the same
 * name half the time.
 *
 * Local time, not UTC: the person reading the name is the one who made the
 * file, and "the export I took after practice yesterday" is a local thought.
 *
 * Plain arithmetic in common code, with the offset passed in, for the reason
 * [platformUtcOffsetSeconds] gives: the calendar maths is then the same
 * everywhere and testable without a platform. The conversion from a day
 * number to a civil date is Howard Hinnant's, which is the standard one and
 * is correct for dates before 1970 as well as after.
 */
fun fileTimestamp(epochMillis: Long, utcOffsetSeconds: Int): String {
    val local = epochMillis + utcOffsetSeconds * 1000L
    val days = local.floorDiv(MILLIS_PER_DAY)
    val millisOfDay = local.mod(MILLIS_PER_DAY)

    // Days shifted to an era beginning on 0000-03-01, so that the leap day
    // falls at the end of a year and every month has a fixed length.
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val dayOfEra = z - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val dayOfMonth = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0

    val hour = millisOfDay / (60 * 60 * 1000)
    val minute = millisOfDay / (60 * 1000) % 60
    val second = millisOfDay / 1000 % 60

    return "${pad(year, 4)}-${pad(month, 2)}-${pad(dayOfMonth, 2)}_" +
        "${pad(hour, 2)}-${pad(minute, 2)}-${pad(second, 2)}"
}

private fun pad(value: Long, width: Int): String = value.toString().padStart(width, '0')
