// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The app's single source of wall-clock time.
 *
 * Everything that needs "now" -- scheduling, review logs, created/modified
 * stamps -- goes through here rather than calling a platform clock directly.
 *
 * The point is portability. `System.currentTimeMillis()` is a JVM API and does
 * not exist on Kotlin/Wasm, so scattering it through the algorithm, model and
 * repository layers would pin those layers to the JVM. Routing every caller
 * through one object means the browser port replaces one function body instead
 * of chasing down several dozen call sites.
 *
 * [kotlin.time.Clock] is stdlib, so this already compiles on every Kotlin
 * target. When the code moves into a shared Kotlin Multiplatform module this
 * can stay exactly as it is, or become an `expect`/`actual` pair if a platform
 * ever needs something different.
 */
object Time {

    /** Milliseconds since the Unix epoch, UTC. */
    @OptIn(ExperimentalTime::class)
    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    /**
     * The device's current offset from UTC, in seconds; positive east of
     * Greenwich. Includes daylight saving time where it is in effect.
     *
     * This is the one genuinely platform-specific part of this object, and the
     * only line the browser port has to replace -- there it becomes
     * `-Date().getTimezoneOffset() * 60`. Callers that need a local calendar
     * date take the offset as a parameter so they stay pure and testable.
     */
    fun utcOffsetSeconds(): Int =
        java.util.TimeZone.getDefault().getOffset(now()) / 1000
}
