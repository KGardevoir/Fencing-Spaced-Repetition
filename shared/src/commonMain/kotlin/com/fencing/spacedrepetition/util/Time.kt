// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The app's single source of wall-clock time.
 *
 * Everything that needs "now" -- scheduling, review logs, created/modified
 * stamps -- goes through here rather than calling a platform clock directly,
 * so a new target has one place to adapt instead of several dozen call sites.
 */
object Time {

    /** Milliseconds since the Unix epoch, UTC. */
    @OptIn(ExperimentalTime::class)
    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * The device's current offset from UTC, in seconds; positive east of
     * Greenwich. Includes daylight saving time where it is in effect.
     */
    fun utcOffsetSeconds(): Int = platformUtcOffsetSeconds(now())
}

/**
 * The one genuinely platform-specific value in this module.
 *
 * There is no multiplatform way to read the device's timezone offset without
 * taking on a dependency, so this is the seam. The JVM actual reads the
 * default `java.util.TimeZone`; a browser actual is
 * `-Date().getTimezoneOffset() * 60`.
 *
 * Callers that need a local calendar date take the offset as a parameter
 * rather than calling this directly, which keeps that arithmetic pure and
 * testable in [commonTest].
 */
internal expect fun platformUtcOffsetSeconds(atEpochMillis: Long): Int
